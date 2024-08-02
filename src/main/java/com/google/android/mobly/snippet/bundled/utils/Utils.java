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

import android.app.UiAutomation;
import android.os.Build;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.bundled.SmsSnippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.util.Log;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Utils {

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

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

    /**
     * Wait on a specific snippet event.
     *
     * <p>This allows a snippet to wait on another SnippetEvent as long as they know the name and
     * callback id. Commonly used to make async calls synchronous, see {@link
     * SmsSnippet#waitForSms()} waitForSms} for example usage.
     *
     * @param callbackId String callbackId that we want to wait on.
     * @param eventName String event name that we are waiting on.
     * @param timeout int timeout in milliseconds for how long it will wait for the event.
     * @return SnippetEvent if one was received.
     * @throws Throwable if interrupted while polling for event completion. Throws TimeoutException
     *     if no snippet event is received.
     */
    public static SnippetEvent waitForSnippetEvent(
            String callbackId, String eventName, Integer timeout) throws Throwable {
        String qId = EventCache.getQueueId(callbackId, eventName);
        LinkedBlockingDeque<SnippetEvent> q = EventCache.getInstance().getEventDeque(qId);
        SnippetEvent result;
        try {
            result = q.pollFirst(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw e.getCause();
        }

        if (result == null) {
            throw new TimeoutException(
                    String.format(
                            Locale.ROOT,
                            "Timed out waiting(%d millis) for SnippetEvent: %s",
                            timeout,
                            callbackId));
        }
        return result;
    }

    /**
     * A function interface that is used by lambda functions signaling an async operation is still
     * going on.
     */
    public interface Predicate {
        boolean waitCondition() throws Throwable;
    }

    /**
     * Simplified API to invoke an instance method by reflection.
     *
     * <p>Sample usage:
     *
     * <pre>
     *   boolean result = (boolean) Utils.invokeByReflection(
     *           mWifiManager,
     *           "setWifiApEnabled", null /* wifiConfiguration * /, true /* enabled * /);
     * </pre>
     *
     * @param instance Instance of object defining the method to call.
     * @param methodName Name of the method to call. Can be inherited.
     * @param args Variadic array of arguments to supply to the method. Their types will be used to
     *     locate a suitable method to call. Subtypes, primitive types, boxed types, and {@code
     *     null} arguments are properly handled.
     * @return The return value of the method, or {@code null} if no return value.
     * @throws NoSuchMethodException If no suitable method could be found.
     * @throws Throwable The exception raised by the method, if any.
     */
    public static Object invokeByReflection(Object instance, String methodName, Object... args)
            throws Throwable {
        // Java doesn't know if invokeByReflection(instance, name, null) means that the array is
        // null or that it's a non-null array containing a single null element. We mean the latter.
        // Silly Java.
        if (args == null) {
            args = new Object[] {null};
        }
        // Can't use Class#getMethod(Class<?>...) because it expects that the passed in classes
        // exactly match the parameters of the method, and doesn't handle superclasses.
        Method method = null;
        METHOD_SEARCHER:
        for (Method candidateMethod : instance.getClass().getMethods()) {
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
                if (args[i] == null) {
                    // Null is assignable to anything except primitives.
                    if (declaredParams[i].isPrimitive()) {
                        continue METHOD_SEARCHER;
                    }
                } else {
                    // Allow autoboxing during reflection by wrapping primitives.
                    Class<?> declaredClass = Primitives.wrap(declaredParams[i]);
                    Class<?> actualClass = Primitives.wrap(args[i].getClass());
                    TypeToken<?> declaredParamType = TypeToken.of(declaredClass);
                    TypeToken<?> actualParamType = TypeToken.of(actualClass);
                    if (!declaredParamType.isSupertypeOf(actualParamType)) {
                        continue METHOD_SEARCHER;
                    }
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

    /**
     * Convert a byte array (binary data) to a hexadecimal string (ASCII) representation.
     *
     * <p>[\x01\x02] -&gt; "0102"
     *
     * @param bytes The array of byte to convert.
     * @return a String with the ASCII hex representation.
     */
    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

   public static void adaptShellPermissionIfRequired(Context context) throws Throwable {
      if (Build.VERSION.SDK_INT >= 29) {
        Log.d("Elevating permission require to enable support for privileged operation in Android Q+");
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
          Class<?> cls = Class.forName("android.app.UiAutomation");
          Method destroyMethod = cls.getDeclaredMethod("destroy");
          destroyMethod.invoke(uia);
        } catch (NoSuchMethodException
            | IllegalAccessException
            | ClassNotFoundException
            | InvocationTargetException e) {
          throw new RuntimeException("Failed to cleaup Ui Automation", e);
        }
      }
    }
}
