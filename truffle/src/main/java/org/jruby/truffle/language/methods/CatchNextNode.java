/*
 * Copyright (c) 2013, 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.language.methods;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.jruby.truffle.language.RubyNode;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.language.control.NextException;

/**
 * Catch a {@code next} jump at the root of a method.
 */
public class CatchNextNode extends RubyNode {

    @Child private RubyNode body;

    private final BranchProfile nextProfile = BranchProfile.create();

    public CatchNextNode(RubyContext context, SourceSection sourceSection, RubyNode body) {
        super(context, sourceSection);
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return body.execute(frame);
        } catch (NextException e) {
            nextProfile.enter();
            return e.getResult();
        }
    }

}