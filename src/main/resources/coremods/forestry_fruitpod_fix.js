// Forestry Fruit Pod Server Crash Fix
// Patches forestry.arboriculture.genetics.TreeRoot.setFruitBlock to skip the
// RenderUtil.markForUpdate() call on a dedicated server.
//
// Root cause: setFruitBlock() calls RenderUtil.markForUpdate(pos) which
// creates an invokedynamic lambda referencing net.minecraft.client.Minecraft.
// Even though DistExecutor.runWhenOn(Dist.CLIENT, ...) guards the actual call,
// Forge's class verifier crashes with "Attempted to load class
// net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER" as soon as
// the lambda bootstrap class (which directly references Minecraft) is loaded.
// This happens during server-side worldgen when trees try to spawn fruit pods.
//
// The forestry-renderutil-fix mod only adds null-checks inside the lambda body
// (lambda$null$0), but the crash occurs earlier — at class loading time when
// the invokedynamic bootstrap in markForUpdate resolves the lambda class.
//
// Fix: Remove the ALOAD + INVOKESTATIC pair that calls RenderUtil.markForUpdate
// inside setFruitBlock. Fruit pods are placed correctly; only the client-side
// "mark chunk for re-render" notification is suppressed, which is harmless on
// a dedicated server.

function initializeCoreMod() {
    return {
        'ForestryFruitPodFix': {
            'target': {
                'type': 'METHOD',
                'class': 'forestry.arboriculture.genetics.TreeRoot',
                'methodName': 'setFruitBlock',
                'methodDesc': '(Lnet/minecraft/world/IWorld;Lgenetics/api/individual/IGenome;Lforestry/api/arboriculture/genetics/IAlleleFruit;FLnet/minecraft/util/math/BlockPos;)Z'
            },
            'transformer': function(method) {
                var Opcodes        = Java.type('org.objectweb.asm.Opcodes');
                var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');

                var instructions = method.instructions;

                // Find INVOKESTATIC forestry/core/utils/RenderUtil.markForUpdate
                var renderUtilCall = null;
                var iter = instructions.iterator();
                while (iter.hasNext()) {
                    var insn = iter.next();
                    if (insn.getOpcode() === Opcodes.INVOKESTATIC &&
                            insn instanceof MethodInsnNode) {
                        if (insn.owner === 'forestry/core/utils/RenderUtil' &&
                                insn.name === 'markForUpdate') {
                            renderUtilCall = insn;
                            break;
                        }
                    }
                }

                if (renderUtilCall === null) {
                    // Could not find the call — leave method unchanged
                    return method;
                }

                // The instruction immediately before the INVOKESTATIC is the
                // ALOAD that pushes the BlockPos argument onto the stack.
                // Remove both to cleanly drop the call without leaving a
                // dangling value on the stack.
                var prevInsn = renderUtilCall.getPrevious();

                instructions.remove(renderUtilCall);

                if (prevInsn !== null) {
                    var op = prevInsn.getOpcode();
                    // ALOAD = 25, ALOAD_0..ALOAD_3 = 42..45
                    if (op === Opcodes.ALOAD ||
                            op === Opcodes.ALOAD_0 ||
                            op === Opcodes.ALOAD_1 ||
                            op === Opcodes.ALOAD_2 ||
                            op === Opcodes.ALOAD_3) {
                        instructions.remove(prevInsn);
                    }
                }

                return method;
            }
        }
    };
}
