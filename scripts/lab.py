#!/usr/bin/env python3
import argparse
import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parent.parent
SERVICES = {'edge', 'waiting-api', 'waiting-worker', 'booking', 'worker', 'fake-ext',
            'queue-redis', 'booking-redis', 'mysql'}


def execute(command, *, env=None, capture=False, timeout=600):
    result = subprocess.run(command, cwd=ROOT, env=env, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f'{command[0]} {command[1]} failed (exit {result.returncode})')
    return result.stdout.strip() if capture else ''


def private_file(path, content):
    with open(path, 'x', encoding='utf-8') as handle:
        os.chmod(path, 0o600)
        handle.write(content)


class Lab:
    def __init__(self, state_dir, *, create=False, port='18080'):
        self.directory = state_dir
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.lock = open(self.directory / '.lock', 'a')
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        state = self.directory / 'state.json'
        self.env_path = self.directory / 'runtime.env'
        if not state.exists():
            if not create:
                raise RuntimeError('No local stack exists. Run scripts/lab.sh up first.')
            if not port.isdecimal() or not 0 <= int(port) <= 65535:
                raise RuntimeError('LAB_HTTP_PORT must be between 0 and 65535.')
            owner = uuid.uuid4().hex
            values = {
                'COMPOSE_PROJECT_NAME': 'ticketing-lab-' + owner[:12],
                'LAB_OWNER': owner,
                'LAB_HTTP_PORT': port,
                'WAIT_SECRET': secrets.token_hex(32),
                'QUEUE_ADMIN_KEY': secrets.token_hex(24),
                'LAB_DB_PASSWORD': secrets.token_hex(24),
                'LAB_MYSQL_ROOT_PASSWORD': secrets.token_hex(24),
            }
            private_file(self.env_path, ''.join(f'{key}={value}\n' for key, value in values.items()))
            private_file(state, json.dumps({'project': values['COMPOSE_PROJECT_NAME'], 'owner': owner}) + '\n')
        self.state = json.loads(state.read_text())
        if not re.fullmatch(r'ticketing-lab-[0-9a-f]{12}', self.state['project']):
            raise RuntimeError('Invalid local project identity.')
        self.values = dict(line.split('=', 1) for line in self.env_path.read_text().splitlines())
        if (self.values['COMPOSE_PROJECT_NAME'] != self.state['project']
                or self.values['LAB_OWNER'] != self.state['owner']):
            raise RuntimeError('Local state and environment identities disagree.')
        if self.env_path.stat().st_mode & 0o077:
            raise RuntimeError('runtime.env must have mode 0600.')
        self.environment = dict(os.environ)
        self.environment.update(self.values)
        self.environment['COMPOSE_MENU'] = 'false'
        self.base = ['docker', 'compose', '--project-name', self.state['project'],
                     '--env-file', str(self.env_path), '--file', str(ROOT / 'docker-compose.yml')]

    def compose(self, *args, capture=False, timeout=600):
        return execute(self.base + list(args), env=self.environment, capture=capture, timeout=timeout)

    def check_ownership(self):
        project = self.state['project']
        for kind, listing, label_path in (
                ('container', ['docker', 'ps', '-aq'], 'Config'),
                ('volume', ['docker', 'volume', 'ls', '-q'], None),
                ('network', ['docker', 'network', 'ls', '-q'], None)):
            identifiers = execute(listing + ['--filter', f'label=com.docker.compose.project={project}'], capture=True).split()
            if not identifiers:
                continue
            objects = json.loads(execute(['docker', kind, 'inspect', *identifiers], capture=True))
            for obj in objects:
                labels = obj[label_path]['Labels'] if label_path else obj['Labels']
                if (labels or {}).get('lab.ticketing.owner') != self.state['owner']:
                    raise RuntimeError(f'Refusing to modify a {kind} with a different owner.')

    def up(self, build=True):
        self.check_ownership()
        if build:
            execute(['./gradlew', '--console=plain', 'bootJar'])
        self.compose('up', '--detach', '--wait', '--wait-timeout', '180',
                     '--build' if build else '--no-build')

    def down(self, remove_volumes=False):
        self.check_ownership()
        args = ['down', '--timeout', '15']
        if remove_volumes:
            args += ['--volumes']
        self.compose(*args)
        if remove_volumes:
            available = set(execute(['docker', 'image', 'ls', '--format', '{{.Repository}}:{{.Tag}}'], capture=True).splitlines())
            for app in ('waiting', 'booking', 'worker', 'fake-ext'):
                tag = self.state['project'] + '-' + app + ':local'
                if tag not in available:
                    continue
                image = json.loads(execute(['docker', 'image', 'inspect', tag], capture=True))[0]
                if (image['Config'].get('Labels') or {}).get('lab.ticketing.owner') != self.state['owner']:
                    raise RuntimeError('Refusing to remove an image with a different owner.')
                execute(['docker', 'image', 'rm', tag], capture=True)

    def reset(self):
        self.check_ownership()
        self.compose('down', '--volumes', '--timeout', '15')
        self.up(build=False)

    def status(self):
        raw = self.compose('ps', '--all', '--format', 'json', capture=True)
        try:
            result = json.loads(raw or '[]')
            return result if isinstance(result, list) else [result]
        except json.JSONDecodeError:
            return [json.loads(line) for line in raw.splitlines() if line]

    def assert_healthy(self):
        states = {item['Service']: item for item in self.status()}
        if set(states) != SERVICES:
            raise RuntimeError('The stack does not contain the expected nine services.')
        failed = [name for name, item in states.items()
                  if item['State'] != 'running' or item.get('Health') != 'healthy']
        if failed:
            raise RuntimeError('Services not ready: ' + ', '.join(failed))

    def endpoint(self):
        address = self.compose('port', 'edge', '8080', capture=True).splitlines()[0]
        if not address.startswith('127.0.0.1:'):
            raise RuntimeError('The edge listener must bind to loopback.')
        return 'http://' + address

    def request(self, path):
        request = urllib.request.Request(self.endpoint() + path, headers={'Accept': 'application/json'})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            response = opener.open(request, timeout=8)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.loads(response.read())

    def sql(self, query):
        command = 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=TCP -h 127.0.0.1 -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B -e "$1"'
        return self.compose('exec', '-T', 'mysql', 'sh', '-c', command, '_', query, capture=True)

    def smoke(self):
        self.assert_healthy()
        request_id = str(uuid.uuid4())
        status, waiting = self.request('/__lab/waiting/' + request_id)
        if status != 200 or waiting != {'requestId': request_id, 'service': 'waiting', 'mode': 'stub'}:
            raise RuntimeError('Waiting probe returned an unexpected result.')
        status, booking = self.request('/__lab/booking/' + request_id)
        expected = {'requestId': request_id, 'service': 'booking', 'mode': 'stub',
                    'dependency': {'requestId': request_id, 'service': 'fake-ext', 'mode': 'stub'}}
        if status != 200 or booking != expected:
            raise RuntimeError('Booking/provider correlation failed.')
        if self.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1") != '1':
            raise RuntimeError('Initial migration is missing or duplicated.')
        if self.sql('SELECT COUNT(*) FROM bk_ticket_session') != '0':
            raise RuntimeError('The stub path must not create inventory or bookings.')
        print('lab: nine services healthy; waiting and booking/provider correlation passed; migration applied once.')
        return {'requestId': request_id, 'waiting': waiting, 'booking': booking}

    def diagnostics(self, destination):
        try:
            output = self.compose('logs', '--no-color', '--tail', '150', capture=True)
        except Exception:
            output = 'Container logs unavailable.\n'
        for key in ('WAIT_SECRET', 'QUEUE_ADMIN_KEY', 'LAB_DB_PASSWORD', 'LAB_MYSQL_ROOT_PASSWORD'):
            output = output.replace(self.values[key], '[REDACTED]')
        (destination / 'compose.log').write_text(output)


def compose_smoke():
    output = ROOT / 'build/reports/compose-smoke'
    output.mkdir(parents=True, exist_ok=True)
    report = {'recordedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'revision': execute(['git', 'rev-parse', 'HEAD'], capture=True),
              'workingTreeChanged': bool(execute(['git', 'status', '--porcelain'], capture=True)),
              'status': 'running', 'checks': []}
    control = 'ticketing-lab-control-' + uuid.uuid4().hex
    control_created = False
    directory = tempfile.mkdtemp(prefix='ticketing-lab-ci-')
    try:
        lab = Lab(Path(directory), create=True, port='0')
        try:
            lab.up()
            report['journey'] = lab.smoke()
            report['checks'].append('startup, migration, and correlated HTTP journey')
            lab.compose('stop', '--timeout', '5', 'fake-ext')
            try:
                status, body = lab.request('/__lab/booking/' + str(uuid.uuid4()))
                if status != 503 or body.get('code') != 'walking_skeleton_provider_unavailable':
                    raise RuntimeError('Provider outage did not return the specified 503.')
                report['checks'].append('provider outage returns 503')
            finally:
                lab.compose('up', '--detach', '--no-deps', '--wait', '--wait-timeout', '90', 'fake-ext')
            lab.smoke()
            report['checks'].append('provider recovery restores the journey')
            for service in ('queue-redis', 'booking-redis'):
                lab.compose('exec', '-T', service, 'redis-cli', 'SET', 'lab:reset-probe', 'present', capture=True)
            lab.sql('CREATE TABLE lab_reset_probe (id INT PRIMARY KEY); INSERT INTO lab_reset_probe VALUES (1)')
            execute(['docker', 'volume', 'create', '--label', 'lab.ticketing.test=reset-control', control], capture=True)
            control_created = True
            lab.down()
            lab.up(build=False)
            for service in ('queue-redis', 'booking-redis'):
                if lab.compose('exec', '-T', service, 'redis-cli', 'GET', 'lab:reset-probe', capture=True) != 'present':
                    raise RuntimeError('down/up did not preserve Redis data.')
            if lab.sql('SELECT COUNT(*) FROM lab_reset_probe') != '1':
                raise RuntimeError('down/up did not preserve MySQL data.')
            report['checks'].append('down/up preserves data in all three stores')
            lab.reset()
            for service in ('queue-redis', 'booking-redis'):
                if lab.compose('exec', '-T', service, 'redis-cli', 'EXISTS', 'lab:reset-probe', capture=True) != '0':
                    raise RuntimeError('reset did not clear Redis data.')
            if lab.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'ticketing' AND table_name = 'lab_reset_probe'") != '0':
                raise RuntimeError('reset did not clear MySQL data.')
            execute(['docker', 'volume', 'inspect', control], capture=True)
            lab.smoke()
            report['checks'].append('reset clears owned data, reapplies migration, and preserves an unrelated volume')
            report['status'] = 'passed'
        except BaseException as error:
            report['status'] = 'interrupted' if isinstance(error, (KeyboardInterrupt, SystemExit)) else 'failed'
            report['error'] = str(error)
            raise
        finally:
            try:
                try:
                    lab.diagnostics(output)
                except Exception as error:
                    report['diagnosticsError'] = str(error)
                try:
                    lab.down(remove_volumes=True)
                finally:
                    if control_created:
                        execute(['docker', 'volume', 'rm', control], capture=True)
                report['cleanup'] = 'completed'
            except Exception as error:
                report['cleanup'] = str(error)
                report['recoveryState'] = directory
                report['status'] = 'failed'
                raise
            finally:
                (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    finally:
        if report.get('cleanup') == 'completed':
            shutil.rmtree(directory)
        else:
            print('lab: cleanup incomplete; private recovery state retained at ' + directory, file=sys.stderr)
    print('lab: isolated Compose smoke passed; resources cleaned up.')


def main():
    parser = argparse.ArgumentParser(description='Run and verify the local walking skeleton.')
    parser.add_argument('command', choices=['up', 'status', 'smoke', 'down', 'reset', 'smoke-ci'])
    args = parser.parse_args()
    execute(['docker', 'compose', 'version'], capture=True)
    if args.command == 'smoke-ci':
        compose_smoke()
        return
    lab = Lab(ROOT / '.lab', create=args.command == 'up', port=os.environ.get('LAB_HTTP_PORT', '18080'))
    if args.command == 'up':
        lab.up()
        print('lab: ready at ' + lab.endpoint())
    elif args.command == 'status':
        for item in lab.status():
            print(f"{item['Service']}: {item['State']} ({item.get('Health', 'unknown')})")
    elif args.command == 'smoke':
        lab.smoke()
    elif args.command == 'down':
        lab.down()
        print('lab: stopped; data volumes retained.')
    elif args.command == 'reset':
        lab.reset()
        lab.smoke()
        print('lab: owned data reset; ready at ' + lab.endpoint())


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print('lab: ' + str(error), file=sys.stderr)
        sys.exit(1)
