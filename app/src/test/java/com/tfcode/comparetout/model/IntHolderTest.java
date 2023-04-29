/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.model;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class IntHolderTest {

    @Test
    public void getCopy_does_not_modify_original() {
        IntHolder testObject = new IntHolder();
        List<Integer> copyObject = testObject.getCopyOfInts();
        copyObject.remove(1);
        assertTrue (testObject.ints.contains(1));
        assertFalse(copyObject.contains(1));
    }

    @Test
    public void toString_format() {
        IntHolder testObject = new IntHolder();
        assertEquals("[0, 1, 2, 3, 4, 5, 6]", testObject.toString());
    }
}