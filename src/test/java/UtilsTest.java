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

import static com.google.android.mobly.snippet.bundled.utils.Utils.invokeByReflection;

import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link com.google.android.mobly.snippet.bundled.utils.Utils} */
public class UtilsTest {
    public static final class ReflectionTest_HostClass {
        public Object returnSame(List<String> arg) {
            return arg;
        }

        public Object returnSame(int arg) {
            return arg;
        }

        public Object multiArgCall(Object arg1, Object arg2, boolean returnArg1) {
            if (returnArg1) {
                return arg1;
            }
            return arg2;
        }

        public boolean returnTrue() {
            return true;
        }

        public void throwsException() throws IOException {
            throw new IOException("Example exception");
        }
    }

    @Test
    public void testInvokeByReflection_Obj() throws Throwable {
        List<?> sampleList = Collections.singletonList("sampleList");
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        Object ret = invokeByReflection(hostClass, "returnSame", sampleList);
        Truth.assertThat(ret).isSameAs(sampleList);
    }

    @Test
    public void testInvokeByReflection_Null() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        Object ret = invokeByReflection(hostClass, "returnSame", (Object) null);
        Truth.assertThat(ret).isNull();
    }

    @Test
    public void testInvokeByReflection_NoArg() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        boolean ret = (boolean) invokeByReflection(hostClass, "returnTrue");
        Truth.assertThat(ret).isTrue();
    }

    @Test
    public void testInvokeByReflection_Primitive() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        Object ret = invokeByReflection(hostClass, "returnSame", 5);
        Truth.assertThat(ret).isEqualTo(5);
    }

    @Test
    public void testInvokeByReflection_MultiArg() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        Object arg1 = new Object();
        Object arg2 = new Object();
        Object ret =
                invokeByReflection(hostClass, "multiArgCall", arg1, arg2, true /* returnArg1 */);
        Truth.assertThat(ret).isSameAs(arg1);
        ret =
                Utils.invokeByReflection(
                        hostClass, "multiArgCall", arg1, arg2, false /* returnArg1 */);
        Truth.assertThat(ret).isSameAs(arg2);
    }

    @Test
    public void testInvokeByReflection_NoMatch() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        Truth.assertThat(List.class.isAssignableFrom(Object.class)).isFalse();
        try {
            invokeByReflection(hostClass, "returnSame", new Object());
            Assert.fail();
        } catch (NoSuchMethodException e) {
            Truth.assertThat(e.getMessage())
                    .contains("UtilsTest$ReflectionTest_HostClass#returnSame(Object)");
        }
    }

    @Test
    public void testInvokeByReflection_UnwrapException() throws Throwable {
        ReflectionTest_HostClass hostClass = new ReflectionTest_HostClass();
        try {
            invokeByReflection(hostClass, "throwsException");
            Assert.fail();
        } catch (IOException e) {
            Truth.assertThat(e.getMessage()).isEqualTo("Example exception");
        }
    }
}
