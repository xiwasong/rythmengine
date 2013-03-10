package com.greenlaw110.rythm.internal;

import com.greenlaw110.rythm.Rythm;
import com.greenlaw110.rythm.RythmEngine;

import java.util.Locale;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 10/03/13
 * Time: 7:10 AM
 * To change this template use File | Settings | File Templates.
 */
public class CacheKey {
    public static String i18nMsg(RythmEngine engine, String key, boolean useFormat) {
        Locale locale = engine.conf().locale();
        return Rythm.substitute("@1-i18nM-@2-@3-@4", key, locale, useFormat, engine);
    }
    
    public static String i18nBundle(RythmEngine engine, Locale locale) {
        return Rythm.substitute("i18nB-@1-@2", locale, engine);
    }
}
