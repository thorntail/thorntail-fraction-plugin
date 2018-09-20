/*
 * Copyright 2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin.utils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 9/20/18
 */
public class ChecksumUtilTest {

    @Test
    public void shouldConvertBytesToHex() {
        byte[] input = {101, 10, 38, -116, 52, -124, 122, -21, -110, 37, 44, -8, 21, -63, -10, 121, 73, -124, -57, -102};
        assertEquals(ChecksumUtil.toHex(input), "650a268c34847aeb92252cf815c1f6794984c79a");
    }

    @Test
    public void shouldConvertOneToHex() {
        byte[] input = {1};
        assertEquals(ChecksumUtil.toHex(input), "01");
    }

    @Test
    public void shouldConvertTwoBytesToHex() {
        byte[] input = {127, 1};
        assertEquals(ChecksumUtil.toHex(input), "7f01");
    }

    @Test
    public void shouldConvertNegativeBytesToHex() {
        byte[] input = {-127, 1};
        assertEquals(ChecksumUtil.toHex(input), "8101");
    }



}