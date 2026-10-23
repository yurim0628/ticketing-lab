# Experiment: [title]

> Blank report template. Replace the prompts with recorded facts; remove sections only when their omission is explained. Do not substitute illustrative numbers for measurements.

## Status and question

- Status: [planned / running / completed / inconclusive]
- Question: [one concrete question]
- Hypothesis: [testable statement]
- Success criterion: [observable condition, defined before running]
- Falsifying observation: [result that would reject the hypothesis]
- Implementation and regression tests: [repository-relative links]

## Environment

| Item | Recorded value |
|---|---|
| Date and time zone | [fill in] |
| Commit and working-tree changes | [fill in] |
| OS and architecture | [fill in] |
| CPU and memory | [fill in] |
| JDK and Gradle | [fill in] |
| Docker engine and resource allocation | [fill in, or explain why not applicable] |
| Dependency versions and image digests | [fill in] |
| Application and datastore configuration | [non-secret values or checked-in configuration links] |
| Competing workload on the host | [fill in] |

## Workload and failure model

- Initial state and seed: [how to recreate the data]
- Operations and request identity: [what each client does]
- Concurrency and arrival pattern: [how load is generated]
- Warm-up, duration, and repetitions: [fill in]
- Injected failures and timing: [exact controls]
- Recovery procedure: [fill in]
- Excluded failure modes: [limits of this experiment]

## Reproduction

Record commands from a clean checkout, including setup, execution, evidence collection, and cleanup. Identify separate terminal sessions or processes. Commands must reference available scripts and must not contain credentials.

```text
[Exact setup commands]
[Exact execution commands]
[Exact evidence collection commands]
[Exact cleanup commands]
```

## Raw evidence

Link to the actual logs, request outcomes, state snapshots, metrics, and test reports. Document file formats, units, and any redaction. Preserve failed runs and explain missing observations.

| Artifact | Location | What it records |
|---|---|---|
| [fill in] | [repository-relative path or durable public link] | [fill in] |

## Results

Record per-run outcomes before aggregating. State units, sample counts, aggregation methods, error counts, and the treatment of timeouts. Include correctness checks even when the main question concerns performance.

| Run | Observed outcome | Correctness checks | Evidence |
|---|---|---|---|
| [fill in] | [fill in] | [fill in] | [fill in] |

## Interpretation and limitations

- Conclusion: [supported / rejected / inconclusive, tied to the stated criterion]
- Explanation: [separate observations from inferred causes]
- Alternative explanations: [what else could explain the result]
- Limits: [scale, environment, sampling, and untested failure modes]
- Decision: [implementation or configuration choice justified by this evidence]
- Follow-up: [smallest next experiment or regression check]
