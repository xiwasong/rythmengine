package com.greenlaw110.rythm.internal.parser;

import com.greenlaw110.rythm.internal.IContext;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 12/02/13
 * Time: 7:20 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class RemoveLeadingLineBreakAndSpacesParser extends ParserBase implements IRemoveLeadingLineBreakAndSpaces {
    protected RemoveLeadingLineBreakAndSpacesParser(IContext context) {
        super(context);
    }
}
