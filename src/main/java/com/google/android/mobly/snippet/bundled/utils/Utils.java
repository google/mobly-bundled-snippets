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

public final class Utils {
    private Utils() {}

    /**
     * Waits util a condition is met.
     *
     * <p>This is often used to wait for asynchronous operations to finish and the system to reach a
     * desired state.
     *
     * <p>If the predicate function throws an exception and interrupts the waiting, the exception
     * will be wrapped in an {@link InterruptedException}.
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    /**
     * A function interface that is used by lambda functions signaling an async operation is still
     * going on.
     */
    public interface Predicate {
        boolean waitCondition() throws Exception;
    }
}
