/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */

package org.jruby.truffle.runtime.subsystems;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.LineToProbesMap;
import org.jruby.truffle.nodes.RubyGuards;
import org.jruby.truffle.nodes.methods.DeclarationContext;
import org.jruby.truffle.runtime.RubyArguments;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.layouts.Layouts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttachmentsManager {

    private final RubyContext context;
    private final LineToProbesMap lineToProbesMap;
    private final Map<LineLocation, List<Instrument>> attachments = new HashMap<>();

    public AttachmentsManager(RubyContext context) {
        this.context = context;

        // TODO CS 28-Feb-15 this is global isn't it?

        lineToProbesMap = new LineToProbesMap();
        context.getEnv().instrumenter().install(lineToProbesMap);
    }

    public synchronized void attach(String file, int line, final DynamicObject block) {
        assert RubyGuards.isRubyProc(block);

        final String info = String.format("Truffle::Primitive.attach@%s:%d", file, line);

        final AdvancedInstrumentResultListener listener = new AdvancedInstrumentResultListener() {

            @Override
            public void onExecution(Node node, VirtualFrame virtualFrame, Object result) {
            }

            @Override
            public void onFailure(Node node, VirtualFrame virtualFrame, RuntimeException exception) {
            }

        };

        final AdvancedInstrumentRootFactory rootFactory = new AdvancedInstrumentRootFactory() {

            @Override
            public AdvancedInstrumentRoot createInstrumentRoot(Probe probe, Node node) {
                return new AdvancedInstrumentRoot() {

                    @Node.Child
                    private DirectCallNode callNode;

                    @Override
                    public Object executeRoot(Node node, VirtualFrame frame) {
                        final DynamicObject binding = Layouts.BINDING.createBinding(context.getCoreLibrary().getBindingFactory(), frame.materialize());

                        if (callNode == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();

                            callNode = insert(Truffle.getRuntime().createDirectCallNode(Layouts.PROC.getCallTargetForType(block)));

                            if (callNode.isCallTargetCloningAllowed()) {
                                callNode.cloneCallTarget();
                            }

                            if (callNode.isInlinable()) {
                                callNode.forceInlining();
                            }
                        }

                        callNode.call(frame, RubyArguments.pack(
                                Layouts.PROC.getMethod(block),
                                Layouts.PROC.getDeclarationFrame(block),
                                null,
                                Layouts.PROC.getSelf(block),
                                Layouts.PROC.getBlock(block),
                                DeclarationContext.METHOD,
                                new Object[]{binding}));

                        return null;
                    }

                    @Override
                    public String instrumentationInfo() {
                        return info;
                    }

                };
            }

        };

        final Source source = context.getSourceCache().getBestSourceFuzzily(file);

        final LineLocation lineLocation = source.createLineLocation(line);

        List<Instrument> instruments = attachments.get(lineLocation);

        if (instruments == null) {
            instruments = new ArrayList<>();
            attachments.put(lineLocation, instruments);
        }

        for (Probe probe : lineToProbesMap.findProbes(lineLocation)) {
            if (probe.isTaggedAs(StandardSyntaxTag.STATEMENT)) {
                instruments.add(context.getEnv().instrumenter().attach(probe, listener, rootFactory, null, info));
                return;
            }
        }

        throw new RuntimeException("couldn't find a statement!");
    }

    public synchronized void detach(String file, int line) {
        final Source source = context.getSourceCache().getBestSourceFuzzily(file);

        final LineLocation lineLocation = source.createLineLocation(line);

        final List<Instrument> instruments = attachments.remove(lineLocation);

        if (instruments != null) {
            for (Instrument instrument : instruments) {
                instrument.dispose();
            }
        }
    }

}
