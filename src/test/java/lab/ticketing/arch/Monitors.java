package lab.ticketing.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;

/**
 * Finds monitors (synchronized) in bytecode. A virtual thread that blocks inside synchronized
 * holds on to its carrier thread (pinning, JDK 21). Checks both the method modifier (ACC_SYNCHRONIZED) and blocks (MONITORENTER).
 */
final class Monitors {

    private Monitors() {
    }

    /** List of "Class#method (block|method)" entries. Passes only when empty. */
    static List<String> find(JavaClasses classes) {
        List<String> found = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            javaClass.getSource().ifPresent(source -> {
                try (InputStream in = source.getUri().toURL().openStream()) {
                    scan(javaClass.getName(), in.readAllBytes(), found);
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot read " + source.getUri(), e);
                }
            });
        }
        return found;
    }

    private static void scan(String className, byte[] bytecode, List<String> found) {
        new ClassReader(bytecode).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                    found.add(className + "#" + name + " (method)");
                }
                return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.MONITORENTER) {
                            found.add(className + "#" + name + " (block)");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
