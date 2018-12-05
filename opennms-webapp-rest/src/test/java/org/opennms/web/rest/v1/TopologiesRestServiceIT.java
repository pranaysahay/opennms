/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.web.rest.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.core.test.rest.AbstractSpringJerseyRestTestCase;
import org.opennms.netmgt.topologies.service.api.OnmsTopology;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyDao;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyEdge;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyException;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyPort;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyUpdater;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyVertex;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-service.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-databasePopulator.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-svclayer.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-cxf-common.xml",
        "classpath:/applicationContext-rest-test.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
@Transactional
public class TopologiesRestServiceIT extends AbstractSpringJerseyRestTestCase {

    @Autowired
    private OnmsTopologyDao m_topologyDao;
    private RestTestTopologyUpdater m_updater = new RestTestTopologyUpdater();

    private class RestTestTopologyUpdater implements OnmsTopologyUpdater {

        OnmsTopology topology; ;
        @Override
        public OnmsTopology getTopology() {
        if (topology == null) {
              topology= new OnmsTopology();
              try {
                topology.getVertices().add(OnmsTopologyVertex.create("A", "vertexA", "addressA", "system"));
                topology.getVertices().add(OnmsTopologyVertex.create("B", "vertexB", "addressB", "system"));
                OnmsTopologyPort portA10 = OnmsTopologyPort.create(topology.getVertex("A"), 10);
                portA10.setPort("Port A10");
                portA10.setAddr("ab00000010");
                OnmsTopologyPort portB49 = OnmsTopologyPort.create(topology.getVertex("B"), 49);
                portB49.setPort("Port B49");
                portB49.setAddr("abc0000049");
                topology.getEdges().add(OnmsTopologyEdge.create("A:B",portA10,portB49 ));
               
            } catch (OnmsTopologyException e) {
                e.printStackTrace();
                topology = new OnmsTopology();
            }   
        }
        return topology;
        }

        @Override
        public String getProtocol() {
            return "TESTREST";
        }

        @Override
        public String getName() {
            return "RestTestTopologyUpdater";
        }
        
    }
    
    @Override
    protected void afterServletStart() {
        MockLogAppender.setupLogging(true, "DEBUG");
        try {
            m_topologyDao.register(m_updater);
        } catch (OnmsTopologyException e) {
            e.printStackTrace();
        }
        
    }

    @Test
    public void getTestRestTopology() throws Exception {
        String xml;
        xml = sendRequest(GET, "/topologies", 200);
        assertTrue(xml.equals("[TESTREST]"));
        
        xml = sendRequest(GET, "/topologies/count",200);
        assertTrue(xml.equals("1"));
        
        xml = sendRequest(GET, "/topologies/TESTREST",200);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:ns0=\"http://www.w3.org/1999/xlink\"><key id=\"namespace\" for=\"graph\" attr.name=\"namespace\" attr.type=\"string\"/><key id=\"label\" for=\"graph\" attr.name=\"label\" attr.type=\"string\"/><key id=\"label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/><key id=\"iconKey\" for=\"node\" attr.name=\"iconKey\" attr.type=\"string\"/><key id=\"tooltipText\" for=\"node\" attr.name=\"tooltipText\" attr.type=\"string\"/><key id=\"nodeID\" for=\"node\" attr.name=\"nodeID\" attr.type=\"string\"/><key id=\"sourceifindex\" for=\"edge\" attr.name=\"sourceifindex\" attr.type=\"int\"/><key id=\"tooltipText\" for=\"edge\" attr.name=\"tooltipText\" attr.type=\"string\"/><key id=\"targetifindex\" for=\"edge\" attr.name=\"targetifindex\" attr.type=\"int\"/><graph id=\"TESTREST\"><data key=\"namespace\">TESTREST</data><data key=\"label\">TESTREST Topology</data><node id=\"A\"><data key=\"label\">vertexA</data><data key=\"iconKey\">system</data><data key=\"tooltipText\">addressA</data><data key=\"nodeID\">A</data></node><node id=\"B\"><data key=\"label\">vertexB</data><data key=\"iconKey\">system</data><data key=\"tooltipText\">addressB</data><data key=\"nodeID\">B</data></node><edge id=\"A:B\" source=\"A\" target=\"B\"><data key=\"sourceifindex\">10</data><data key=\"tooltipText\">Port A10Port B49</data><data key=\"targetifindex\">49</data></edge></graph></graphml>", xml);
    }


}
