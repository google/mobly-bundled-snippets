/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.mobly.snippet.bundled.utils;

import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Utils {
    /**
     * A function interface that is used by lambda functions signaling an async operation is still
     * going on.
     */
    public interface Predicate {
        boolean waitCondition() throws Throwable;
    }

    private Utils() {}

    /**
     * Waits util a condition is met.
     *
     * <p>This is often used to wait for asynchronous operations to finish and the system to reach a
     * desired state.
     *
     * <p>If the predicate function throws an exception and interrupts the waiting, the exception
     * will be wrapped in an {@link RuntimeException}.
     *
     * @param predicate A lambda function that specifies the condition to wait for. This function
     *     should return true when the desired state has been reached.
     * @param timeout The number of seconds to wait for before giving up.
     * @return true if the operation finished before timeout, false otherwise.
     */
    public static boolean waitUntil(Utils.Predicate predicate, int timeout) {
        timeout *= 10;
        try {
            while (!predicate.waitCondition() && timeout >= 0) {
                Thread.sleep(100);
                timeout -= 1;
            }
            if (predicate.waitCondition()) {
                return true;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public static Object invokeByReflection(Object instance, String methodName, Object... args)
        throws Throwable {
        // Can't use Class#getMethod(Class<?>...) because it expects that the passed in classes
        // exactly match the parameters of the method, and doesn't handle superclasses.
        Method method = null;
        METHOD_SEARCHER: for (Method candidateMethod : instance.getClass().getMethods()) {
            // getMethods() returns only public methods, so we don't need to worry about checking
            // whether the method is accessible.
            if (!candidateMethod.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] declaredParams = candidateMethod.getParameterTypes();
            if (declaredParams.length != args.length) {
                continue;
            }
            for (int i = 0; i < declaredParams.length; i++) {
                // Allow autoboxing during reflection by wrapping primitives.
                Class<?> declaredClass = Primitives.wrap(declaredParams[i]);
                Class<?> actualClass = Primitives.wrap(args[i].getClass());
                TypeToken<?> declaredParamType = TypeToken.of(declaredClass);
                TypeToken<?> actualParamType = TypeToken.of(actualClass);
                if (!declaredParamType.isSupertypeOf(actualParamType)) {
                    continue METHOD_SEARCHER;
                }
            }
            method = candidateMethod;
            break;
        }
        if (method == null) {
            StringBuilder methodString =
                new StringBuilder(instance.getClass().getName())
                    .append('#')
                .append(methodName)
                .append('(');
            for (int i = 0; i < args.length - 1; i++) {
                methodString.append(args[i].getClass().getSimpleName()).append(", ");
            }
            if (args.length > 0) {
                methodString.append(args[args.length - 1].getClass().getSimpleName());
            }
            methodString.append(')');
            throw new NoSuchMethodException(methodString.toString());
        }
        try {
            Object result = method.invoke(instance, args);
            return result;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
