/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.store.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Unit tests for {@link DocumentPath}.
 */
public class DocumentPathTest {

    @Test
    public void testConstruction() {
        DocumentPath path = path("root.a.b");
        assertEquals(path.pathElements(), Arrays.asList("root", "a", "b"));
        assertEquals(path("root.a"), path.parent());
        assertEquals(path("b"), path.childPath());
    }

    @Test
    public void testAncestry() {
        DocumentPath path = path("root");
        assertEquals(path.childPath(), null);
        DocumentPath path1 = path("root.a.b");
        DocumentPath path2 = path("root.a.d");
        DocumentPath path3 = path("root.a.b.c");
        DocumentPath lca = DocumentPath.leastCommonAncestor(Arrays.asList(path1, path2, path3));
        assertEquals(path("root.a"), lca);
        assertTrue(path1.isAncestorOf(path3));
        assertFalse(path1.isAncestorOf(path2));
        assertTrue(path3.isDescendentOf(path3));
        assertTrue(path3.isDescendentOf(path1));
        assertFalse(path3.isDescendentOf(path2));
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testExceptions() {
        DocumentPath parentPath = path("root.a.b");
        DocumentPath path2 = exceptions("nodeName", parentPath);
        exception.expect(IllegalDocumentNameException.class);
        DocumentPath path1 = exceptions("node|name", parentPath);
    }

    @Test
    public void comparePaths() {
        DocumentPath one = path("root");
        DocumentPath four = path("root.a.b.c.d");
        DocumentPath difFour = path("root.e.c.b.a");
        assertEquals(-1, one.compareTo(four));
        assertEquals(1, four.compareTo(one));
        assertEquals(4, difFour.compareTo(four));
        assertEquals(0, difFour.compareTo(difFour));
    }

    private static DocumentPath exceptions(String nodeName, DocumentPath path) {
        return new DocumentPath(nodeName, path);
    }

    private static DocumentPath path(String path) {
        return DocumentPath.from(path.replace(".", DocumentPath.DEFAULT_SEPARATOR));
    }
}
