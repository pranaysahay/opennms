/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2007-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.dao.support;

import static org.easymock.EasyMock.expect;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opennms.core.test.ConfigurationTestUtils;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.config.CollectdConfigFactory;
import org.opennms.netmgt.config.DataCollectionConfigDao;
import org.opennms.netmgt.config.datacollection.ResourceType;
import org.opennms.netmgt.dao.api.LocationMonitorDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.filter.FilterDao;
import org.opennms.netmgt.filter.FilterDaoFactory;
import org.opennms.netmgt.model.LocationMonitorIpInterface;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsResource;
import org.opennms.netmgt.rrd.RrdUtils;
import org.opennms.netmgt.rrd.jrobin.JRobinRrdStrategy;
import org.opennms.test.FileAnticipator;
import org.opennms.test.mock.EasyMockUtils;

public class FindTopLevelResourcesTest {
    private EasyMockUtils m_easyMockUtils;

    private NodeDao m_nodeDao;
    private LocationMonitorDao m_locationMonitorDao;
    private CollectdConfigFactory m_collectdConfig;
    private DataCollectionConfigDao m_dataCollectionConfigDao;
    private DefaultResourceDao m_resourceDao;

    private FileAnticipator m_fileAnticipator;

    private FilterDao m_filterDao;

    @Before
    public void setUp() throws Exception {
        m_fileAnticipator = new FileAnticipator();

        m_easyMockUtils = new EasyMockUtils();
        m_nodeDao = m_easyMockUtils.createMock(NodeDao.class);
        m_locationMonitorDao = m_easyMockUtils.createMock(LocationMonitorDao.class);
        m_dataCollectionConfigDao = m_easyMockUtils.createMock(DataCollectionConfigDao.class);
        m_filterDao = m_easyMockUtils.createMock(FilterDao.class);

        FilterDaoFactory.setInstance(m_filterDao);

        expect(m_filterDao.getActiveIPAddressList("IPADDR IPLIKE *.*.*.*")).andReturn(new ArrayList<InetAddress>(0)).anyTimes();

        m_easyMockUtils.replayAll();
        InputStream stream = ConfigurationTestUtils.getInputStreamForResource(this, "/collectdconfiguration-testdata.xml");
        m_collectdConfig = new CollectdConfigFactory(stream, "localhost", false);
        m_easyMockUtils.verifyAll();

        m_resourceDao = new DefaultResourceDao();
        m_resourceDao.setNodeDao(m_nodeDao);
        m_resourceDao.setLocationMonitorDao(m_locationMonitorDao);
        m_resourceDao.setCollectdConfig(m_collectdConfig);
        m_resourceDao.setRrdDirectory(m_fileAnticipator.getTempDir());
        m_resourceDao.setDataCollectionConfigDao(m_dataCollectionConfigDao);

        RrdUtils.setStrategy(new JRobinRrdStrategy());
    }

    @After
    public void tearDown() {
        m_fileAnticipator.tearDown();
    }

    private void setStoreByForeignSource(boolean storeByForeignSource) {
        System.setProperty("org.opennms.rrd.storeByForeignSource", Boolean.toString(storeByForeignSource));
    }

    @Test
    public void testFindTopLevelResources_discoveredNodesWithoutStoreByForeignSource() throws Exception {
        setStoreByForeignSource(false);
        execute_testFindTopLevelResources_discoveredNodes();
    }

    @Test
    public void testFindTopLevelResources_discoveredNodesWithStoreByForeignSource() throws Exception {
        setStoreByForeignSource(true);
        execute_testFindTopLevelResources_discoveredNodes();
    }

    /*
     * On environments where all the nodes have been discovered (i.e. they are not part of a requisition),
     * the top level resources are always going to be built using NodeResourceType.
     * no matter if storeByForeignSource is enabled or not.
     */
    private void execute_testFindTopLevelResources_discoveredNodes() throws Exception {
        List<OnmsNode> nodes = new ArrayList<OnmsNode>();

        OnmsNode n1 = new OnmsNode();
        n1.setId(1);
        n1.setLabel("node1");
        OnmsIpInterface ip1 = new OnmsIpInterface();
        ip1.setId(11);
        ip1.setIpAddress(InetAddressUtils.addr("10.0.0.1"));
        ip1.setNode(n1);
        n1.addIpInterface(ip1);
        nodes.add(n1);

        expect(m_dataCollectionConfigDao.getLastUpdate()).andReturn(new Date(System.currentTimeMillis())).times(2);
        expect(m_dataCollectionConfigDao.getConfiguredResourceTypes()).andReturn(new HashMap<String, ResourceType>());
        expect(m_nodeDao.get(n1.getId())).andReturn(n1).times(2); // TODO ResponseTimeResourceType is called as many time as resources the node has.
        expect(m_locationMonitorDao.findStatusChangesForNodeForUniqueMonitorAndInterface(n1.getId())).andReturn(new ArrayList<LocationMonitorIpInterface>(0));
        expect(m_nodeDao.findAll()).andReturn(nodes);

        File snmpDir = m_fileAnticipator.tempDir("snmp");
        File nodeDir = m_fileAnticipator.tempDir(snmpDir, n1.getId().toString());
        m_fileAnticipator.tempFile(nodeDir, "foo" + RrdUtils.getExtension());

        File responseDir = m_fileAnticipator.tempDir("response");
        File ipDir = m_fileAnticipator.tempDir(responseDir, ip1.getIpAddress().getHostAddress());
        m_fileAnticipator.tempFile(ipDir, "foo" + RrdUtils.getExtension());

        m_easyMockUtils.replayAll();
        m_resourceDao.afterPropertiesSet();

        List<OnmsResource> resources = m_resourceDao.findTopLevelResources();
        Assert.assertNotNull(resources);
        Collections.sort(resources);
        Assert.assertEquals(1, resources.size());
        List<OnmsResource> children = resources.get(0).getChildResources();
        Collections.sort(children);
        Assert.assertEquals(2, children.size());
        Assert.assertEquals("node[1].responseTime[10.0.0.1]", children.get(0).getId());
        Assert.assertEquals("node[1].nodeSnmp[]", children.get(1).getId());

        m_easyMockUtils.verifyAll();
    }

    @Test
    public void testFindTopLevelResources_provisionedNodesWithoutStoreByForeignSource() throws Exception {
        execute_testFindTopLevelResources_provisionedNodes(false);
    }

    @Test
    public void testFindTopLevelResources_provisionedNodesWithStoreByForeignSource() throws Exception {
        execute_testFindTopLevelResources_provisionedNodes(true);
    }

    /*
     * On environments where all the nodes are part of a requisition (i.e. they have been provisioned)
     * the top level resources are always going to be built using NodeSourceResourceType only
     * if storeByForeignSource is enabled, otherwise they are all going to built using NodeResourceType.
     */
    private void execute_testFindTopLevelResources_provisionedNodes(boolean storeByForeignSource) throws Exception {
        setStoreByForeignSource(storeByForeignSource);
        List<OnmsNode> nodes = new ArrayList<OnmsNode>();

        OnmsNode n1 = new OnmsNode();
        n1.setId(1);
        n1.setLabel("node1");
        n1.setForeignSource("Junit");
        n1.setForeignId("node1");
        OnmsIpInterface ip1 = new OnmsIpInterface();
        ip1.setId(11);
        ip1.setIpAddress(InetAddressUtils.addr("10.0.0.1"));
        ip1.setNode(n1);
        n1.addIpInterface(ip1);
        nodes.add(n1);

        expect(m_dataCollectionConfigDao.getLastUpdate()).andReturn(new Date(System.currentTimeMillis())).times(2);
        expect(m_dataCollectionConfigDao.getConfiguredResourceTypes()).andReturn(new HashMap<String, ResourceType>());
        expect(m_nodeDao.get(n1.getId())).andReturn(n1).times(2); // TODO ResponseTimeResourceType is the responsible for this.
        expect(m_nodeDao.findAll()).andReturn(nodes);
        if (storeByForeignSource) {
            expect(m_nodeDao.findByForeignId(n1.getForeignSource(), n1.getForeignId())).andReturn(n1).times(1);            
        } else {
            expect(m_locationMonitorDao.findStatusChangesForNodeForUniqueMonitorAndInterface(n1.getId())).andReturn(new ArrayList<LocationMonitorIpInterface>(0));
        }

        File snmpDir = m_fileAnticipator.tempDir("snmp");
        if (storeByForeignSource) {
            File fsDir = m_fileAnticipator.tempDir(snmpDir, "fs");
            File node1fsDir = m_fileAnticipator.tempDir(fsDir, n1.getForeignSource());
            File node1Dir = m_fileAnticipator.tempDir(node1fsDir, n1.getForeignId());
            m_fileAnticipator.tempFile(node1Dir, "foo" + RrdUtils.getExtension());
        } else {
            File nodeDir = m_fileAnticipator.tempDir(snmpDir, n1.getId().toString());
            m_fileAnticipator.tempFile(nodeDir, "foo" + RrdUtils.getExtension());
        }

        File responseDir = m_fileAnticipator.tempDir("response");
        File ipDir = m_fileAnticipator.tempDir(responseDir, ip1.getIpAddress().getHostAddress());
        m_fileAnticipator.tempFile(ipDir, "foo" + RrdUtils.getExtension());

        m_easyMockUtils.replayAll();
        m_resourceDao.afterPropertiesSet();

        List<OnmsResource> resources = m_resourceDao.findTopLevelResources();
        Assert.assertNotNull(resources);
        Collections.sort(resources);
        Assert.assertEquals(1, resources.size());
        List<OnmsResource> children = resources.get(0).getChildResources();
        Collections.sort(children);

        Assert.assertEquals(2, children.size());
        if (storeByForeignSource) {
            Assert.assertEquals("nodeSource[Junit%3Anode1].responseTime[10.0.0.1]", children.get(0).getId());
            Assert.assertEquals("nodeSource[Junit%3Anode1].nodeSnmp[]", children.get(1).getId());
        } else {
            Assert.assertEquals("node[1].responseTime[10.0.0.1]", children.get(0).getId());
            Assert.assertEquals("node[1].nodeSnmp[]", children.get(1).getId());
        }

        m_easyMockUtils.verifyAll();
    }

    @Test
    public void testFindTopLevelResources_hybridNodesWithoutStoreByForeignSource() throws Exception {
        execute_testFindTopLevelResources_hybridNodes(false);
    }

    @Test
    public void testGetTopLevelResources_hybridNodesWithStoreByForeignSource() throws Exception {
        execute_testFindTopLevelResources_hybridNodes(true);
    }

    /*
     * On hybrid environments where some nodes have been discovered and other nodes are part of a requisition,
     * the top level resources are always going to be built using NodeResourceType only if storeByForeignSource
     * is disabled.
     * But, if storeByForeignSource is enabled, the resources associated with discovered nodes are going to be
     * built by NodeResourceType, and the resources associated with requisitioned nodes are going to be built by
     * NodeSourceResourceType.
     */
    private void execute_testFindTopLevelResources_hybridNodes(boolean storeByForeignSource) throws Exception {
        setStoreByForeignSource(storeByForeignSource);
        List<OnmsNode> nodes = new ArrayList<OnmsNode>();

        OnmsNode n1 = new OnmsNode(); // discovered node
        n1.setId(1);
        n1.setLabel("node1");
        OnmsIpInterface ip1 = new OnmsIpInterface();
        ip1.setId(11);
        ip1.setIpAddress(InetAddressUtils.addr("10.0.0.1"));
        ip1.setNode(n1);
        n1.addIpInterface(ip1);
        nodes.add(n1);

        OnmsNode n2 = new OnmsNode(); // requisitioned node
        n2.setId(2);
        n2.setLabel("node2");
        n2.setForeignSource("Junit");
        n2.setForeignId("node2");
        OnmsIpInterface ip2 = new OnmsIpInterface();
        ip2.setId(12);
        ip2.setIpAddress(InetAddressUtils.addr("10.0.0.2"));
        ip2.setNode(n2);
        n2.addIpInterface(ip2);
        nodes.add(n2);

        expect(m_dataCollectionConfigDao.getLastUpdate()).andReturn(new Date(System.currentTimeMillis())).times(3);
        expect(m_dataCollectionConfigDao.getConfiguredResourceTypes()).andReturn(new HashMap<String, ResourceType>());
        expect(m_locationMonitorDao.findStatusChangesForNodeForUniqueMonitorAndInterface(n1.getId())).andReturn(new ArrayList<LocationMonitorIpInterface>(0));
        if (storeByForeignSource) {
            expect(m_nodeDao.findByForeignId(n2.getForeignSource(), n2.getForeignId())).andReturn(n2).times(1);
        } else {
            expect(m_locationMonitorDao.findStatusChangesForNodeForUniqueMonitorAndInterface(n2.getId())).andReturn(new ArrayList<LocationMonitorIpInterface>(0));
        }
        expect(m_nodeDao.get(n1.getId())).andReturn(n1).times(2); // TODO ResponseTimeResourceType is the responsible for this.
        expect(m_nodeDao.get(n2.getId())).andReturn(n2).times(2); // TODO ResponseTimeResourceType is the responsible for this.
        expect(m_nodeDao.findAll()).andReturn(nodes);

        File snmpDir = m_fileAnticipator.tempDir("snmp");

        File node1Dir = m_fileAnticipator.tempDir(snmpDir, n1.getId().toString());
        m_fileAnticipator.tempFile(node1Dir, "foo" + RrdUtils.getExtension());

        if (storeByForeignSource) {
            File fsDir = m_fileAnticipator.tempDir(snmpDir, "fs");
            File node2fsDir = m_fileAnticipator.tempDir(fsDir, n2.getForeignSource());
            File node2Dir = m_fileAnticipator.tempDir(node2fsDir, n2.getForeignId());
            m_fileAnticipator.tempFile(node2Dir, "foo" + RrdUtils.getExtension());
        } else {
            File node2Dir = m_fileAnticipator.tempDir(snmpDir, n2.getId().toString());
            m_fileAnticipator.tempFile(node2Dir, "foo" + RrdUtils.getExtension());
        }

        File responseDir = m_fileAnticipator.tempDir("response");
        File ip1Dir = m_fileAnticipator.tempDir(responseDir, ip1.getIpAddress().getHostAddress());
        m_fileAnticipator.tempFile(ip1Dir, "foo" + RrdUtils.getExtension());
        File ip2Dir = m_fileAnticipator.tempDir(responseDir, ip2.getIpAddress().getHostAddress());
        m_fileAnticipator.tempFile(ip2Dir, "foo" + RrdUtils.getExtension());

        m_easyMockUtils.replayAll();
        m_resourceDao.afterPropertiesSet();

        List<OnmsResource> resources = m_resourceDao.findTopLevelResources();
        Assert.assertNotNull(resources);
        Collections.sort(resources);
        Assert.assertEquals(2, resources.size());

        if (storeByForeignSource) {
            OnmsResource r1 = resources.get(0); // parent resource for the provisioned node 
            List<OnmsResource> children1 = r1.getChildResources();
            Collections.sort(children1);
            Assert.assertEquals("nodeSource[Junit%3Anode2]", r1.getId());
            Assert.assertEquals("nodeSource[Junit%3Anode2].responseTime[10.0.0.2]", children1.get(0).getId());
            Assert.assertEquals("nodeSource[Junit%3Anode2].nodeSnmp[]", children1.get(1).getId());

            OnmsResource r2 = resources.get(1); // parent resource for the discovered node
            Assert.assertEquals("node[1]", r2.getId());
            List<OnmsResource> children2 = r2.getChildResources();
            Collections.sort(children2);
            Assert.assertEquals(2, children2.size());
            Assert.assertEquals("node[1].responseTime[10.0.0.1]", children2.get(0).getId());
            Assert.assertEquals("node[1].nodeSnmp[]", children2.get(1).getId());

        } else {
            OnmsResource r1 = resources.get(1); // parent resource for the provisioned node 
            List<OnmsResource> children1 = r1.getChildResources();
            Collections.sort(children1);
            Assert.assertEquals("node[2]", r1.getId());
            Assert.assertEquals("node[2].responseTime[10.0.0.2]", children1.get(0).getId());
            Assert.assertEquals("node[2].nodeSnmp[]", children1.get(1).getId());

            OnmsResource r2 = resources.get(0); // parent resource for the discovered node
            Assert.assertEquals("node[1]", r2.getId());
            List<OnmsResource> children2 = r2.getChildResources();
            Collections.sort(children2);
            Assert.assertEquals(2, children2.size());
            Assert.assertEquals("node[1].responseTime[10.0.0.1]", children2.get(0).getId());
            Assert.assertEquals("node[1].nodeSnmp[]", children2.get(1).getId());
        }

        m_easyMockUtils.verifyAll();
    }

}
