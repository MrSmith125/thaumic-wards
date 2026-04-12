// Dynamic Lights Log Spam Suppression
// Patches atomicstryker.dynamiclights.server.DynamicLights.addLightSource to
// remove two verbose LOGGER.info() calls that fire every time a torch item
// entity is created.
//
// Root cause: addLightSource() calls LOGGER.info() twice:
//   1. "Calling addLightSource on entity {}, dimensionLocationPath {}"
//      — fires on every EntityJoinWorld event for any lit dropped item.
//   2. "Successfully registered DynamicLight on Entity: {} in list {}"
//      — fires on every successful registration and passes the *entire*
//        ConcurrentLinkedQueue as the second argument, printing hundreds
//        of registered light sources into a single log line.
//
// On a busy server with torches or glowstone being dropped constantly this
// produces massive log I/O. There is no config option to disable logging in
// this mod version (1.16.5.1).
//
// Fix: Walk the instruction list of addLightSource and remove every
// INVOKEINTERFACE call to Logger.info that is preceded by an LDC loading one
// of the two known spam strings, together with all instructions that push
// arguments for that call (walking backwards from the LDC to clear the stack).

function initializeCoreMod() {
    return {
        'DynamicLightsLogSuppress': {
            'target': {
                'type': 'METHOD',
                'class': 'atomicstryker.dynamiclights.server.DynamicLights',
                'methodName': 'addLightSource',
                'methodDesc': '(Latomicstryker/dynamiclights/server/IDynamicLightSource;)V'
            },
            'transformer': function(method) {
                var Opcodes           = Java.type('org.objectweb.asm.Opcodes');
                var LdcInsnNode       = Java.type('org.objectweb.asm.tree.LdcInsnNode');
                var MethodInsnNode    = Java.type('org.objectweb.asm.tree.MethodInsnNode');
                var AbstractInsnNode  = Java.type('org.objectweb.asm.tree.AbstractInsnNode');

                // The two log strings we want to silence.
                var SPAM_STRINGS = [
                    'Calling addLightSource on entity {}, dimensionLocationPath {}',
                    'Successfully registered DynamicLight on Entity: {} in list {}'
                ];

                var instructions = method.instructions;

                // Collect all LDC nodes whose constant matches a spam string.
                var spamLdcNodes = [];
                var iter = instructions.iterator();
                while (iter.hasNext()) {
                    var insn = iter.next();
                    if (insn instanceof LdcInsnNode) {
                        var cst = insn.cst;
                        for (var i = 0; i < SPAM_STRINGS.length; i++) {
                            if (SPAM_STRINGS[i] === cst) {
                                spamLdcNodes.push(insn);
                                break;
                            }
                        }
                    }
                }

                if (spamLdcNodes.length === 0) {
                    // Nothing matched — leave method unchanged.
                    return method;
                }

                // For each spam LDC node:
                //   - Find the INVOKEINTERFACE Logger.info(...) that follows it
                //     (skip over any intermediate instructions that push args).
                //   - Collect all instructions from ALOAD_0 (GETFIELD LOGGER) up
                //     to and including the INVOKEINTERFACE, then remove them all.
                //
                // The instruction sequence emitted by javac for each call is:
                //   GETSTATIC  DynamicLights.LOGGER                   ← push logger
                //   LDC        "Calling addLightSource on entity..."  ← push format string
                //   ALOAD / GETFIELD / INVOKEVIRTUAL ...              ← push arg1
                //   ALOAD / GETFIELD / INVOKEVIRTUAL ...              ← push arg2 (for 2-arg overload)
                //   INVOKEINTERFACE Logger.info (String, Object, Object) or (String, Object)
                //
                // We walk *forward* from the LDC to locate the matching
                // INVOKEINTERFACE, then *backward* from the LDC to find the
                // GETSTATIC that started the logger push.  Everything in
                // [GETSTATIC .. INVOKEINTERFACE] is removed.

                for (var s = 0; s < spamLdcNodes.length; s++) {
                    var ldcNode = spamLdcNodes[s];

                    // --- Find the INVOKEINTERFACE Logger.info ahead of the LDC ---
                    var infoCall = null;
                    var cursor = ldcNode.getNext();
                    // Allow up to 10 instructions ahead to find it.
                    for (var fwd = 0; fwd < 10 && cursor !== null; fwd++) {
                        if (cursor.getOpcode() === Opcodes.INVOKEINTERFACE &&
                                cursor instanceof MethodInsnNode) {
                            var mn = cursor;
                            if (mn.owner === 'org/apache/logging/log4j/Logger' &&
                                    mn.name === 'info') {
                                infoCall = cursor;
                                break;
                            }
                        }
                        cursor = cursor.getNext();
                    }

                    if (infoCall === null) {
                        // Cannot find closing INVOKEINTERFACE — skip this one.
                        continue;
                    }

                    // --- Find the GETSTATIC for LOGGER behind the LDC ---
                    // Walk backwards until we hit a GETSTATIC or run out.
                    var getstaticNode = null;
                    var back = ldcNode.getPrevious();
                    for (var bwd = 0; bwd < 5 && back !== null; bwd++) {
                        var op = back.getOpcode();
                        if (op === Opcodes.GETSTATIC) {
                            getstaticNode = back;
                            break;
                        }
                        // Stop if we hit something that looks like end of previous
                        // statement (RETURN, GOTO, label pseudo-insns have opcode -1).
                        if (op !== -1 && op !== Opcodes.NOP) {
                            // Keep walking — there may be a label node (opcode -1) between
                            // the previous statement and the GETSTATIC.
                        }
                        back = back.getPrevious();
                    }

                    // Collect the contiguous range [start .. infoCall] to remove.
                    // If we found GETSTATIC use it as start; otherwise use LDC.
                    var startNode = (getstaticNode !== null) ? getstaticNode : ldcNode;

                    // Build the removal list by walking forward from startNode to
                    // infoCall (inclusive).
                    var toRemove = [];
                    var walk = startNode;
                    while (walk !== null) {
                        toRemove.push(walk);
                        if (walk === infoCall) break;
                        walk = walk.getNext();
                    }

                    // Remove all collected instructions.
                    for (var r = 0; r < toRemove.length; r++) {
                        instructions.remove(toRemove[r]);
                    }
                }

                return method;
            }
        }
    };
}
