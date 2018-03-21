/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.telemetry.adapters.netflow.sflow;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.bson.BsonDocument;
import org.junit.Before;
import org.junit.Test;
import org.opennms.netmgt.flows.api.Flow;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class SFlowConverterTest {
    private BsonDocument bsonDocument;

    @Before
    public void setupBsonDocument() throws Exception {
        final URL resourceURL = getClass().getResource("/flows/sflow.json");
        this.bsonDocument = BsonDocument.parse(Files.toString(new File(resourceURL.getFile()), Charsets.UTF_8));
    }

    @Test
    public void canConvertBsonDocument() throws Exception {
        assertThat(bsonDocument.getDocument("data"), notNullValue());
        assertThat(bsonDocument.getInt64("time"), notNullValue());
        assertThat(bsonDocument.getInt64("time").getValue(), is(1521618510235L));
        assertThat(bsonDocument.getDocument("data").getArray("samples"), notNullValue());
        assertThat(bsonDocument.getDocument("data").getArray("samples").size(), is(5));

        final List<Flow> flows = new SFlowConverter().convert(bsonDocument);

        assertThat(flows.size(), is(5));
    }
}
