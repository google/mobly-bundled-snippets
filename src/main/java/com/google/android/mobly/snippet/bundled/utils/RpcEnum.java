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
        mEnums = builder.build();
    }

    /**
     * Get the int value of an enum based on its String value.
     *
     * @param enumString
     * @return
     */
    public int getIntValue(String enumString) {
        return mEnums.get(enumString);
    }

    /**
     * Get the String value of an enum based on its int value.
     *
     * @param enumInt
     * @return
     */
    public String getStringValue(int enumInt) {
        return mEnums.inverse().get(enumInt);
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
