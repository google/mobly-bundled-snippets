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

import com.google.common.collect.ImmutableBiMap;

/**
 * A container type for handling String-Integer enum conversion in Rpc protocol.
 *
 * <p>In Serializing/Deserializing Android API enums, we often need to convert an enum value from
 * one form to another. This container class makes it easier to do so.
 *
 * <p>Once built, an RpcEnum object is immutable.
 */
public class RpcEnum {
    private final ImmutableBiMap<String, Integer> mEnums;

    private RpcEnum(ImmutableBiMap.Builder<String, Integer> builder) {
        mEnums = builder.buildOrThrow();
    }

    /**
     * Get the int value of an enum based on its String value.
     *
     * @param enumString
     * @return
     */
    public int getInt(String enumString) {
        Integer result = mEnums.get(enumString);
        if (result == null) {
            throw new NoSuchFieldError("No int value found for: " + enumString);
        }
        return result;
    }

    /**
     * Get the String value of an enum based on its int value.
     *
     * @param enumInt
     * @return
     */
    public String getString(int enumInt) {
        String result = mEnums.inverse().get(enumInt);
        if (result == null) {
            throw new NoSuchFieldError("No String value found for: " + enumInt);
        }
        return result;
    }

    /** Builder for RpcEnum. */
    public static class Builder {
        private final ImmutableBiMap.Builder<String, Integer> builder;

        public Builder() {
            builder = new ImmutableBiMap.Builder<>();
        }

        /**
         * Add an enum String-Integer pair.
         *
         * @param enumString
         * @param enumInt
         * @return
         */
        public Builder add(String enumString, int enumInt) {
            builder.put(enumString, enumInt);
            return this;
        }

        public RpcEnum build() {
            return new RpcEnum(builder);
        }
    }
}
