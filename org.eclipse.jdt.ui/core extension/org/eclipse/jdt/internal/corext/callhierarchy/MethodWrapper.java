/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.callhierarchy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * This class represents the general parts of a method call (either to or from a
 * method).
 *
 */
public abstract class MethodWrapper implements IAdaptable {
    public static final int DIRECTION_CALLER= 1;
    public static final int DIRECTION_CALLEE= 2;
    
    private Map fElements = null;

    /*
     * A cache of previously found methods. This cache should be searched
     * before adding a "new" method object reference to the list of elements.
     * This way previously found methods won't be searched again.
     */
    private Map fMethodCache;
    private MethodCall fMethodCall;
    private MethodWrapper fParent;
    private int fLevel;

    /**
     * Constructor CallerElement.
     */
    public MethodWrapper(MethodWrapper parent, MethodCall methodCall) {
        Assert.isNotNull(methodCall);

        if (parent == null) {
            setMethodCache(new HashMap());
            fLevel = 1;
        } else {
            setMethodCache(parent.getMethodCache());
            fLevel = parent.getLevel() + 1;
        }

        this.fMethodCall = methodCall;
        this.fParent = parent;
    }

    public Object getAdapter(Class adapter) {
        if (adapter == IJavaElement.class) {
            return getMember();
        }

        return null;
    }

    /**
     * Method getCallerElements.
     * @return The child caller elements of this element
     */
    public MethodWrapper[] getCalls() {
        if (fElements == null) {
            doFindChildren();
        }

        MethodWrapper[] result = new MethodWrapper[fElements.size()];
        int i = 0;

        for (Iterator iter = fElements.keySet().iterator(); iter.hasNext();) {
            MethodCall methodCall = getMethodCallFromMap(fElements, iter.next());
            result[i++] = createMethodWrapper(methodCall);
        }

        return result;
    }

    /**
     * @return int
     */
    public int getLevel() {
        return fLevel;
    }

    /**
     * Method getMethod.
     * @return Object
     */
    public IMember getMember() {
        return getMethodCall().getMember();
    }

    /**
     * @return MethodCall
     */
    public MethodCall getMethodCall() {
        return fMethodCall;
    }

    /**
     * Method getName.
     */
    public String getName() {
        if (getMethodCall() != null) {
            return getMethodCall().getMember().getElementName();
        } else {
            return ""; //$NON-NLS-1$
        }
    }

    /**
     * Method getParent.
     * @return
     */
    public MethodWrapper getParent() {
        return fParent;
    }

    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }

        if (oth == null) {
            return false;
        }

        if (oth.getClass() != getClass()) {
            return false;
        }

        MethodWrapper other = (MethodWrapper) oth;

        if (this.fParent == null) {
            if (other.fParent != null) {
                return false;
            }
        } else {
            if (!this.fParent.equals(other.fParent)) {
                return false;
            }
        }

        if (this.getMethodCall() == null) {
            if (other.getMethodCall() != null) {
                return false;
            }
        } else {
            if (!this.getMethodCall().equals(other.getMethodCall())) {
                return false;
            }
        }

        return true;
    }

    public int hashCode() {
        final int PRIME = 1000003;
        int result = 0;

        if (fParent != null) {
            result = (PRIME * result) + fParent.hashCode();
        }

        if (getMethodCall() != null) {
            result = (PRIME * result) + getMethodCall().getMember().hashCode();
        }

        return result;
    }

    private void setMethodCache(Map methodCache) {
        fMethodCache = methodCache;
    }

    protected abstract String getTaskName();

    private void addCallToCache(MethodCall methodCall) {
        Map cachedCalls = lookupMethod(this.getMethodCall());
        cachedCalls.put(methodCall.getKey(), methodCall);
    }

    /**
     * Method createMethodWrapper.
     * @param method
     * @return MethodWrapper
     */
    protected abstract MethodWrapper createMethodWrapper(MethodCall methodCall);

    private void doFindChildren() {
        Map existingResults = lookupMethod(getMethodCall());

        if (existingResults != null) {
            fElements = new HashMap();
            fElements.putAll(existingResults);
        } else {
            initCalls();

            IProgressMonitor progressMonitor = getProgressMonitor();

            if (progressMonitor != null) {
                progressMonitor.beginTask(getTaskName(), IProgressMonitor.UNKNOWN);
            }

            try {
                performSearch(progressMonitor);
            } finally {
                if (progressMonitor != null) {
                    progressMonitor.done();
                }
            }

            //                ModalContext.run(getRunnableWithProgress(), true, getProgressMonitor(),
            //                    Display.getCurrent());
        }
    }

    /**
     * Determines if the method represents a recursion call (i.e. whether the
     * method call is already in the cache.)
     *
     * @return True if the call is part of a recursion
     */
    public boolean isRecursive() {
        MethodWrapper current = getParent();

        while (current != null) {
            if (getMember().getHandleIdentifier().equals(current.getMember()
                                                                        .getHandleIdentifier())) {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }

    public abstract int getDirection();
    
    /**
     * This method finds the children of the current IMethod (either callers or
     * callees, depending on the concrete subclass.
     * @return The result of the search for children
     */
    protected abstract Map findChildren(IProgressMonitor progressMonitor);

    private Map getMethodCache() {
        return fMethodCache;
    }

    private void initCalls() {
        this.fElements = new HashMap();

        initCacheForMethod();
    }

    /**
     * Looks up a previously created search result in the "global" cache.
     * @param method
     * @return List List of previously found search results
     */
    private Map lookupMethod(MethodCall methodCall) {
        return (Map) getMethodCache().get(methodCall.getKey());
    }

    private void performSearch(IProgressMonitor progressMonitor) {
        fElements = findChildren(progressMonitor);

        for (Iterator iter = fElements.keySet().iterator(); iter.hasNext();) {
            MethodCall methodCall = getMethodCallFromMap(fElements, iter.next());
            addCallToCache(methodCall);
        }
    }

    private MethodCall getMethodCallFromMap(Map elements, Object key) {
        return (MethodCall) elements.get(key);
    }

    private IProgressMonitor getProgressMonitor() {
        // TODO: Figure out what to do with progress monitors
        return new NullProgressMonitor();
    }

    private void initCacheForMethod() {
        Map cachedCalls = new HashMap();
        getMethodCache().put(this.getMethodCall().getKey(), cachedCalls);
    }
    
    /**
     * Allows a visitor to traverse the call hierarchy. The visiting is stopped when
     * a recursive node is reached.
     *  
     * @param visitor
     */
    public void accept(CallHierarchyVisitor visitor) {
        if (getParent() != null && getParent().isRecursive()) {
            return;
        }
        visitor.preVisit(this);
        if (visitor.visit(this)) {
            MethodWrapper[] methodWrappers= getCalls();
            for (int i= 0; i < methodWrappers.length; i++) {
                methodWrappers[i].accept(visitor);
            }
        }
        visitor.postVisit(this);
    }
}