/* 
 * Copyright (C) 2013-2016 The Rythm Engine project
 * for LICENSE and other details see:
 * https://github.com/rythmengine/rythmengine
 */
package org.rythmengine.internal.compiler;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rythmengine.Rythm;
import org.rythmengine.RythmEngine;
import org.rythmengine.exception.CompileException;
import org.rythmengine.exception.RythmException;
import org.rythmengine.extension.IByteCodeEnhancer;
import org.rythmengine.extension.ICodeType;
import org.rythmengine.extension.ITemplateResourceLoader;
import org.rythmengine.internal.CodeBuilder;
import org.rythmengine.internal.IDialect;
import org.rythmengine.internal.RythmEvents;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;
import org.rythmengine.resource.ITemplateResource;
import org.rythmengine.resource.StringTemplateResource;
import org.rythmengine.template.ITemplate;
import org.rythmengine.template.TagBase;
import org.rythmengine.template.TemplateBase;
import org.rythmengine.utils.S;

/**
 * Define the data structure hold template class/template src/generated java src
 */
public class TemplateClass {
    private static final ILogger logger = Logger.get(TemplateClass.class);

    public static final String CN_SUFFIX = "__R_T_C__";

    private static final String NO_INCLUDE_CLASS = "NO_INCLUDE_CLASS";
    private static final ITemplate NULL_TEMPLATE = new TagBase() {
        @Override
        public ITemplate __cloneMe(RythmEngine engine, ITemplate caller) {
            return null;
        }
    };

    /**
     * Store root level template class, e.g. the one that is not an embedded class
     */
    private TemplateClass root;
    private boolean inner = false;
    private RythmEngine engine = null;
    private boolean enhancing = false;
    private transient List<TemplateClass> embeddedClasses = new ArrayList<TemplateClass>();

    /**
     * The fully qualified class name
     */
    private String name;
    private TemplateClass extendedTemplateClass;
    private Set<TemplateClass> includedTemplateClasses = new HashSet<TemplateClass>();
    private String includeTemplateClassNames = null;
    private Map<String, String> includeTagTypes = new HashMap<String, String>();
    private String tagName;

    /**
     * The Java source
     */
    private String javaSource;
    /**
     * The compiled byteCode
     */
    private byte[] javaByteCode;
    /**
     * The enhanced byteCode
     */
    private byte[] enhancedByteCode;
    /**
     * Store a list of import path, i.e. those imports ends with ".*"
     */
    private Set<String> importPaths;
    /**
     * The in JVM loaded class
     */
    private Class<ITemplate> javaClass;
    /**
     * The in JVM loaded package
     */
    private Package javaPackage;
    /**
     * The code type could be HTML, JS, JSON etc
     */
    private ICodeType codeType;
    /**
     * Is this class compiled
     */
    private boolean compiled;
    /**
     * Signatures checksum
     */
    private int sigChecksum;

    /**
     * Mark if this is a valid Rythm Template
     */
    private boolean isValid = true;

    /**
     * CodeBuilder to generate java source code
     * <p/>
     * Could be used to merge state into including template class codeBuilder
     */
    private CodeBuilder codeBuilder;

    /**
     * The ITemplate instance
     */
    private TemplateBase templateInstance;

    /**
     * Store the resource loader class name
     */
    private String resourceLoaderClass;

    /**
     * the template resource
     */
    private ITemplateResource templateResource;

    /* Locks */
    private final ReadWriteLock mutationLock = new ReentrantReadWriteLock();

    /**
     * specify the dialect for the template
     */
    transient private IDialect dialect;

    private String magic = S.random(4);

    public TemplateClass root() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return root;
        } finally {
            lock.unlock();
        }
    }

    private TemplateClass() {
    }

    public boolean isInner() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return inner;
        } finally {
            lock.unlock();
        }
    }

    private RythmEngine engine() {
        return null == engine ? Rythm.engine() : engine;
    }

    public String name0() {
        return name();
    }

    public String name() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return name;
        } finally {
            lock.unlock();
        }
    }

    /*
     * WRITE : includedTemplateClasses, includeTagTypes
     */
    public void addIncludeTemplateClass(TemplateClass tc) {
        final Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            includedTemplateClasses.add(tc);
            includeTagTypes.putAll(tc.includeTagTypes);
        } finally {
            lock.unlock();
        }
    }

    /*
     * WRITE : includeTemplateClassNames
     */
    public String refreshIncludeTemplateClassNames() {
        final Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            if (includedTemplateClasses.isEmpty()) {
                includeTemplateClassNames = NO_INCLUDE_CLASS;
                return NO_INCLUDE_CLASS;
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            for (TemplateClass tc : includedTemplateClasses) {
                if (!first) {
                    sb.append(",");
                }
                else {
                    first = false;
                }
                sb.append(tc.tagName);
            }
            includeTemplateClassNames = sb.toString();
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }


    /*
     *  WRITE : includeTagTypes
     */
    public void setTagType(String tagName, String type) {
        final Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            includeTagTypes.put(tagName, type);
        } finally {
            lock.unlock();
        }
    }

    public boolean returnObject(String tagName) {
        Lock lock = mutationLock.readLock();
        lock.lock();

        try {
            String retType = includeTagTypes.get(tagName);
            if (null != retType) {
                return !"void".equals(retType);
            }
            if (null != extendedTemplateClass) {
                return extendedTemplateClass.returnObject(tagName);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public String serializeIncludeTagTypes() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            if (includeTagTypes.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            boolean empty = true;
            for (Map.Entry<String, String> entry : includeTagTypes.entrySet()) {
                if (!empty) {
                    sb.append(";");
                }
                else {
                    empty = false;
                }
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    /*
     * WRITE : includeTagTypes
     */
    public void deserializeIncludeTagTypes(String s) {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            includeTagTypes = new HashMap<String, String>();
            if (S.isEmpty(s)) {
                return;
            }
            String[] sa = s.split(";");
            for (String s0 : sa) {
                String[] sa0 = s0.split(":");
                if (sa0.length != 2) {
                    throw new IllegalArgumentException("Unknown include tag types string: " + s);
                }
                includeTagTypes.put(sa0[0], sa0[1]);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * If not null then this template is a tag
     */
    public String getTagName() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return tagName;
        } finally {
            lock.unlock();
        }
    }


    /**
     * The template source
     */
    public String getTemplateSource() {
        return getTemplateSource(false);
    }

    public String getTemplateSource(boolean includeRoot) {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            if (null != templateResource) {
                return templateResource.asTemplateContent();
            }
            if (!includeRoot) {
                return "";
            }
            TemplateClass parent = root;
            while ((null != parent) && parent.isInner()) {
                parent = parent.root;
            }
            return null == parent ? "" : parent.getTemplateSource();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Is this template resource coming from a literal String or from a loaded resource like file
     */
    public boolean isStringTemplate() {
        return templateResource instanceof StringTemplateResource;
    }

    public String getResourceLoaderClass() {
        return resourceLoaderClass;
    }


    private TemplateClass(RythmEngine engine) {
        this.engine = null == engine ? null : engine.isSingleton() ? null : engine;
    }

    /**
     * Construct a TemplateClass instance using template source file
     *
     * @param file the template source file
     */
    public TemplateClass(File file, RythmEngine engine) {
        this(engine.resourceManager().get(file), engine);
    }

    /**
     * Construct a TemplateClass instance using template source content or file path
     *
     * @param template
     */
    public TemplateClass(String template, RythmEngine engine) {
        this(engine.resourceManager().get(template), engine);
    }

    /**
     * Construct a TemplateClass instance using template source content or file path
     *
     * @param template
     */
    public TemplateClass(String template, RythmEngine engine, IDialect dialect) {
        this(engine.resourceManager().get(template), engine, dialect);
    }

    public TemplateClass(ITemplateResource resource, RythmEngine engine) {
        this(resource, engine, false);
    }

    public TemplateClass(ITemplateResource resource, RythmEngine engine, IDialect dialect) {
        this(resource, engine, false, dialect);
    }

    /*
     * WRITE : templateResource
     */
    public TemplateClass(ITemplateResource resource, RythmEngine engine, boolean noRefresh) {
        this(engine);
        if (null == resource) {
            throw new NullPointerException();
        }
        //resource.setEngine(engine());
        templateResource = resource;
        if (!noRefresh) {
            refresh_(false);
        }
    }

    /*
     * WRITE : templateResource
     */
    public TemplateClass(ITemplateResource resource, RythmEngine engine, boolean noRefresh, IDialect dialect) {
        this(engine);
        if (null == resource) {
            throw new NullPointerException();
        }
        //resource.setEngine(engine());
        templateResource = resource;
        this.dialect = dialect;
        if (!noRefresh) {
            refresh_(false);
        }
    }

    /**
     * Return the name or key of the template resource
     *
     * @return the key 
     */
    public String getKey() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return null == templateResource ? name : templateResource.getKey().toString();
        } finally {
            lock.unlock();
        }
    }

    private String getKey_() {
        return null == templateResource ? name : templateResource.getKey().toString();
    }

    @SuppressWarnings("unchecked")
    private Class<?> loadJavaClass() throws Exception {
        if (null == javaSource) {
            if (null == javaSource) {
                refresh_(false);
            }
        }
        RythmEngine engine = engine();
        TemplateClassLoader cl = engine.classLoader();
        if (null == cl) {
            throw new NullPointerException();
        }
        Class<?> c = cl.loadClass(name, true);
        if (null == javaClass) {
            javaClass = (Class<ITemplate>) c;
        }
        return c;
    }

    private ITemplate templateInstance_(RythmEngine engine) {
        if (!isValid) {
            return NULL_TEMPLATE;
        }
        if (null == templateInstance) {
            try {
                Class<?> clz = loadJavaClass();
                TemplateBase tmpl = (TemplateBase) clz.newInstance();
                tmpl.__setTemplateClass(this);
                engine.registerTemplate(tmpl);
                //engine.registerTemplate(getFullName(true), tmpl);
                templateInstance = tmpl;
            } catch (RythmException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Error load template instance for " + getKey_(), e);
            }
        }
        if (!engine.isProdMode()) {
            engine.registerTemplate(templateInstance);
            // check parent class change
            Class<?> c = templateInstance.getClass();
            Class<?> pc = c.getSuperclass();
            if (null != pc && !Modifier.isAbstract(pc.getModifiers())) {
                engine.classes().getByClassName(pc.getName());
            }
        }
        return templateInstance;
    }

    private ITemplate asTemplate(ICodeType type, Locale locale, RythmEngine engine) {
        if (null == name || engine.isDevMode()) {
            refresh_(false);
        }
        TemplateBase tmpl = (TemplateBase) templateInstance_(engine).__cloneMe(engine(), null);
        if (tmpl!=null)
          tmpl.__prepareRender(type, locale, engine);
        return tmpl;
    }

    public ITemplate asTemplate(RythmEngine engine) {
        return asTemplate(null, null, engine);
    }

    public ITemplate asTemplate(ITemplate caller, RythmEngine engine) {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return asTemplate_(caller, engine);
        } finally {
            lock.unlock();
        }
    }

    private ITemplate asTemplate_(ITemplate caller, RythmEngine engine) {
        TemplateBase tb = (TemplateBase) caller;
        TemplateBase tmpl = (TemplateBase) templateInstance_(engine).__cloneMe(engine, caller);
        tmpl.__prepareRender(tb.__curCodeType(), tb.__curLocale(), engine);
        return tmpl;
    }

    public boolean refresh() {
        return refresh(false);
    }

    public void buildSourceCode(String includingClassName) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            long start = System.currentTimeMillis();
            importPaths = new HashSet<String>();
            // Possible bug here?
            if (null != codeBuilder) {
                codeBuilder.clear();
            }
            codeBuilder = new CodeBuilder(templateResource.asTemplateContent(), name, tagName, this, engine, dialect);
            codeBuilder.includingCName = includingClassName;
            codeBuilder.build();
            extendedTemplateClass = codeBuilder.getExtendedTemplateClass();
            javaSource = codeBuilder.toString();
            if (logger.isTraceEnabled()) {
                logger.trace("%s ms to generate java source for template: %s", System.currentTimeMillis() - start, getKey_());
            }
        } finally {
            lock.unlock();
        }
    }

    private void buildSourceCode_() {
        long start = System.currentTimeMillis();
        importPaths = new HashSet<String>();
        // Possible bug here?
        if (null != codeBuilder) {
            codeBuilder.clear();
        }
        if (null == dialect) {
            codeBuilder = new CodeBuilder(templateResource.asTemplateContent(), name, tagName, this, engine, null);
        }
        else {
            codeBuilder = dialect.createCodeBuilder(templateResource.asTemplateContent(), name, tagName, this, engine);
        }
        codeBuilder.build();
        extendedTemplateClass = codeBuilder.getExtendedTemplateClass();
        javaSource = codeBuilder.toString();
        if (engine().insideSandbox()) {
            javaSource = CodeBuilder.preventInfiniteLoop(javaSource);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("%s ms to generate java source for template: %s", System.currentTimeMillis() - start, getKey_());
        }
    }


    public void buildSourceCode() {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            buildSourceCode_();
        } finally {
            lock.unlock();
        }
    }

    public void addImportPath(String path) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            if (path == null || path.isEmpty()) {
                return;
            }
            this.importPaths.add(path);
        } finally {
            lock.unlock();
        }
    }

    public void replaceImportPath(Set<String> paths) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.importPaths = paths;
        } finally {
            lock.unlock();
        }
    }

    private boolean refresh_(boolean forceRefresh) {
        if (inner) {
            return false;
        }
        final ITemplateResource templateResource = this.templateResource;
        RythmEngine engine = engine();
        if (!templateResource.isValid()) {
            // it is removed?
            isValid = false;
            engine.classes().remove(this);
            return false;
        }
        ICodeType type = engine.renderSettings.codeType();
        if (null == type) {
            type = templateResource.codeType(engine());
        }
        if (null == type || ICodeType.DefImpl.RAW == type) {
            type = engine.conf().defaultCodeType();
        }
        codeType = type;
        if (null == name) {
            // this is the root level template class
            root = this;
            name = canonicalClassName(templateResource.getSuggestedClassName()) + CN_SUFFIX;
            if (engine.conf().typeInferenceEnabled()) {
                name += ParamTypeInferencer.uuid();
            }
            ITemplateResourceLoader loader = engine().resourceManager().whichLoader(templateResource);
            if (null != loader) {
                Object k = templateResource.getKey();
                tagName = toCanonicalName(k.toString(), loader.getResourceLoaderRoot());
            }
            //name = templateResource.getSuggestedClassName();
            engine.registerTemplateClass(this);
        }

        if (null == javaSource) {
            engine.classCache().loadTemplateClass(this);
            if (null != javaSource) {
                // try refresh extended template class if there is
                Pattern p = Pattern.compile(".*extends\\s+([a-zA-Z0-9_]+)\\s*\\{\\s*\\/\\/<extended_resource_key\\>(.*)\\<\\/extended_resource_key\\>.*", Pattern.DOTALL);
                Matcher m = p.matcher(javaSource);
                if (m.matches()) {
                    String extended = m.group(1);
                    TemplateClassManager tcm = engine().classes();
                    extendedTemplateClass = tcm.getByClassName(extended);
                    if (null == extendedTemplateClass) {
                        String extendedResourceKey = m.group(2);
                        extendedTemplateClass = tcm.getByTemplate(extendedResourceKey);
                        if (null == extendedTemplateClass) {
                            extendedTemplateClass = new TemplateClass(extendedResourceKey, engine());
                            extendedTemplateClass.refresh();
                        }
                    }
                    engine.addExtendRelationship(extendedTemplateClass, this);
                }
            }
        }

        boolean extendedTemplateChanged = false;
        if (extendedTemplateClass != null) {
            extendedTemplateChanged = extendedTemplateClass.refresh(forceRefresh);
        }
        boolean includedTemplateChanged = false;
        boolean includedTemplateClassesIsEmpty;
        includedTemplateClassesIsEmpty = includedTemplateClasses.isEmpty();
        if (includedTemplateClassesIsEmpty && !S.isEmpty(includeTemplateClassNames) && !NO_INCLUDE_CLASS.equals(includeTemplateClassNames)) {
            // just loaded from persistent store
            for (String tcName : includeTemplateClassNames.split(",")) {
                if (S.isEmpty(tcName)) {
                    continue;
                }
                tcName = tcName.trim();
                String fullName = engine().testTemplate(tcName, this, null);
                if (null == fullName) {
                    logger.warn("Unable to load included template class from name: %s", tcName);
                    continue;
                }
                TemplateClass tc = engine().getRegisteredTemplateClass(fullName);
                if (null == tc) {
                    logger.warn("Unable to load included template class from name: %s", tcName);
                    continue;
                }
                includedTemplateClasses.add(tc);
            }
        }
        for (TemplateClass tc : includedTemplateClasses) {
            if (tc.refresh(forceRefresh)) {
                includedTemplateChanged = true;
                break;
            }
        }

        if (extendedTemplateChanged && !forceRefresh) {
            reset_();
            compiled = false;
            engine().restart(new ClassReloadException("extended class changed"));
            refresh_(forceRefresh);
            return true; // pass refresh state to sub template
        }
        // templateResource.refresh() must be put at first so we make sure resource get refreshed

        boolean resourceChanged = templateResource.refresh();
        boolean refresh = resourceChanged || forceRefresh || (null == javaSource) || includedTemplateChanged || extendedTemplateChanged;
        if (!refresh) {
            return false;
        }

        // now start generate source and compile source to byte code
        reset_();
        buildSourceCode_();
        engine().classCache().cacheTemplateClassSource(this); // cache source code for debugging purpose
        if (!codeBuilder.isRythmTemplate()) {
            isValid = false;
            engine().classes().remove(this);
            return false;
        }
        isValid = true;
        //if (!engine().isProd  Mode()) System.err.println(javaSource);
        compiled = false;
        return true;
    }

    /**
     * @return true if this class has changes refreshed, otherwise this class has not been changed yet
     */
    public boolean refresh(boolean forceRefresh) {
        Lock lock = mutationLock.writeLock();
        lock.lock();

        try {
            return refresh_(forceRefresh);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Is this class already compiled but not defined ?
     *
     * @return if the class is compiled but not defined
     */
    public boolean isDefinable() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return compiled && javaClass != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove all java source/ byte code and cache
     */
    public void reset() {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            reset_();
        } finally {
            lock.unlock();
        }
    }

    private void reset_() {
        javaByteCode = null;
        enhancedByteCode = null;
        javaSource = null;
        templateInstance = null;
        for (TemplateClass tc : embeddedClasses) {
            tc.reset();
            engine().classes().remove(tc);
        }
        embeddedClasses.clear();
        engine().classCache().deleteCache(this);
        engine().invalidate(this);
        javaClass = null;
    }

    private String magic() {
        return name + magic;
    }

    private byte[] compile_() {
        long start = System.currentTimeMillis();
        try {
            if (null != javaByteCode) {
                return javaByteCode;
            }
            if (null == javaSource) {
                throw new IllegalStateException("Cannot find java source when compiling " + getKey_());
            }
            engine().classes().compiler.compile(new String[]{name});
            if (logger.isTraceEnabled()) {
                logger.trace("%sms to compile template: %s", System.currentTimeMillis() - start, getKey_());
            }
            return javaByteCode;
        } catch (CompileException.CompilerException e) {
            String cn = e.className;
            TemplateClass tc = S.isEqual(cn, name) ? this : engine().classes().getByClassName(cn);
            if (null == tc) {
                tc = this;
            }
            CompileException ce = new CompileException(engine(), tc, e.javaLineNumber, e.message); // init ce before reset java source to get template line info
            javaSource = null; // force parser to regenerate source. This helps to reload after fixing the tag file compilation failure
            throw ce;
        } catch (NullPointerException e) {
            String clazzName = name;
            TemplateClass tc = engine().classes().getByClassName(clazzName);
            if (this != tc) {
                logger.error("tc is not this");
            }
            if (!this.equals(tc)) {
                logger.error("tc not match this");
            }
            logger.error("NPE encountered when compiling template class:" + name);
            throw e;
        } finally {
            if (logger.isTraceEnabled()) {
                logger.trace("%sms to compile template class %s", System.currentTimeMillis() - start, getKey_());
            }
        }
    }

    /**
     * Compile the class from Java source
     *
     * @return the bytes that comprise the class file
     */
    public byte[] compile() {
        Lock lock = mutationLock.writeLock();
        lock.lock();

        try {
            return compile_();
        } finally {
            lock.unlock();
        }
    }


    /**
     * Used to instruct embedded class byte code needs to be enhanced, but for now
     * let's just use the java byte code as the enhanced bytecode
     */
    public void delayedEnhance(TemplateClass root) {
        Lock lock = mutationLock.writeLock();
        lock.lock();

        try {
            enhancedByteCode = javaByteCode;
            root.embeddedClasses.add(this);
        } finally {
            lock.unlock();
        }
    }

    public byte[] enhance() {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            if (enhancing) {
                throw new IllegalStateException("reenter enhance() call");
            }
            enhancing = true;
            try {
                byte[] bytes = enhancedByteCode;
                if (null == bytes) {
                    bytes = javaByteCode;
                    if (null == bytes) {
                        bytes = compile_();
                    }
                    long start = System.currentTimeMillis();
                    IByteCodeEnhancer en = engine().conf().byteCodeEnhancer();
                    if (null != en) {
                        try {
                            bytes = en.enhance(name, bytes);
                        } catch (Exception e) {
                            logger.warn(e, "Error enhancing template class: %s", getKey_());
                        }
                        if (logger.isTraceEnabled()) {
                            logger.trace("%sms to enhance template class %s", System.currentTimeMillis() - start, getKey_());
                        }
                    }
                    enhancedByteCode = bytes;
                    engine().classCache().cacheTemplateClass(this);
                }
                for (TemplateClass embedded : embeddedClasses) {
                    embedded.enhancedByteCode = null;
                    embedded.enhance();
                }
                return bytes;
            } finally {
                enhancing = false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unload the class
     */
    public void uncompile() {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            javaClass = null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isClass() {
        return !name.endsWith("package-info");
    }

    public String getPackage() {
        int dot = name.lastIndexOf('.');
        return dot > -1 ? name.substring(0, dot) : "";
    }

    public void loadCachedByteCode(byte[] code) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            enhancedByteCode = code;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call back when a class is compiled.
     *
     * @param code The bytecode.
     */
    public void compiled(byte[] code) {
        javaByteCode = code;
        //enhancedByteCode = code;
        compiled = true;
        RythmEvents.COMPILED.trigger(engine(), code);
        enhance();
        //compiled(code, false);
    }

    @Override
    public String toString() {
        return "(compiled:" + compiled + ") " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof TemplateClass) {
            TemplateClass that = (TemplateClass) o;
            return that.getKey_().equals(getKey_());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getKey_().hashCode();
    }


    private static String canonicalClassName(String name) {
        if (S.empty(name)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char[] ca = name.toCharArray();
        int len = ca.length;
        char c = ca[0];
        if (!Character.isJavaIdentifierStart(c)) {
            sb.append('_');
        }
        else {
            sb.append(c);
        }
        for (int i = 1; i < len; ++i) {
            c = ca[i];
            if (!Character.isJavaIdentifierPart(c)) {
                sb.append('_');
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Convert the key to canonical template name
     *
     * @param key  the resource key
     * @param root the resource loader root path
     * @return the canonical name
     */
    private static String toCanonicalName(String key, String root) {
        if (key.startsWith("/") || key.startsWith("\\")) {
            key = key.substring(1);
        }
        if (key.startsWith(root)) {
            key = key.replace(root, "");
        }
        if (key.startsWith("/") || key.startsWith("\\")) {
            key = key.substring(1);
        }
        //if (-1 != pos) key = key.substring(0, pos);
        key = key.replace('/', '.').replace('\\', '.');
        return key;
    }

    public static TemplateClass createInnerClass(String className, byte[] byteCode, TemplateClass parent) {
        TemplateClass tc = new TemplateClass();
        tc.name = className;
        tc.javaByteCode = byteCode;
        //tc.enhancedByteCode = byteCode;
        tc.inner = true;
        tc.root = parent.root();
        return tc;
    }

    public ITemplateResource getTemplateResource() {
        return templateResource;
    }

    public ICodeType getCodeType() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return codeType;
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getImportPaths() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            if (null == importPaths) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(importPaths);
        } finally {
            lock.unlock();
        }
    }

    public String getJavaSource() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return javaSource;
        } finally {
            lock.unlock();
        }
    }

    @Deprecated
    public void setJavaPackage(Package javaPackage) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.javaPackage = javaPackage;
        } finally {
            lock.unlock();
        }
    }

    @Deprecated
    public void setJavaClass(Class<ITemplate> javaClass) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.javaClass = javaClass;
        } finally {
            lock.unlock();
        }
    }

    public CodeBuilder getCodeBuilder() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return codeBuilder;
        } finally {
            lock.unlock();
        }
    }

    public Class<ITemplate> getJavaClass() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return javaClass;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getEnhancedByteCode() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return enhancedByteCode;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getJavaByteCode() {
        return javaByteCode;
    }

    public int getSigChecksum() {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return sigChecksum;
        } finally {
            lock.unlock();
        }
    }

    @Deprecated
    public void setJavaSource(String javaSource) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.javaSource = javaSource;
        } finally {
            lock.unlock();
        }
    }

    @Deprecated
    public void setExtendedTemplateClass(TemplateClass extendedTemplateClass) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.extendedTemplateClass = extendedTemplateClass;
        } finally {
            lock.unlock();
        }
    }

    public void setIncludeTemplateClassNames(String includeTemplateClassNames) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            this.includeTemplateClassNames = includeTemplateClassNames;
        } finally {
            lock.unlock();
        }
    }
}
