//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2003 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2003 Jan 31: Cleaned up some unused imports.
//
// Orignal code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.                                                            
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//       
// For more information contact: 
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
// Tab Size = 8
//

package org.opennms.netmgt.rtc.utils;

import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.rtc.DataManager;
import org.opennms.netmgt.rtc.RTCManager;
import org.opennms.netmgt.rtc.datablock.RTCCategory;
import org.opennms.netmgt.xml.rtc.EuiLevel;
import org.opennms.netmgt.xml.rtc.Header;
import org.opennms.netmgt.xml.rtc.Node;

/**
 * This takes an object of type 'RTCCategory' and creates an XML (bytearray)stream
 * of the format to be sent to the user using classes generated by castor
 *
 * @author 	<A HREF="mailto:sowmya@opennms.org">Sowmya Nataraj</A>
 * @author	<A HREF="http://www.opennms.org">OpenNMS.org</A>
 *
 * @see		org.opennms.netmgt.rtc.datablock.RTCCategory
 * @see		org.opennms.netmgt.xml.rtc.EuiLevel
 */
public class EuiLevelMapper extends Object
{
	/**
	 * The header to be sent out for the availabilty xml(rtceui.xsd)
	 */
	private Header		m_header;

	/**
	 * Constructor
	 */
	public EuiLevelMapper()
	{
		m_header = new Header();
		m_header.setVer("1.9a");
		m_header.setMstation("");
	}

	/**
	 * Convert the 'RTCCategory' object to a 'EuiLevel' object and marshall to XML
	 *
	 * @param rtcCat	the RTCCategory to be converted
	 */
	public EuiLevel convertToEuiLevelXML(RTCCategory rtcCat)
	{
		// current time
		Date curDate = new Date();
		long curTime = curDate.getTime();

		// get the rolling window
		long rWindow = RTCManager.getRollingWindow();

		Category log = ThreadCategory.getInstance(EuiLevelMapper.class);
		if (log.isDebugEnabled())
		{
			log.debug("curdate: " + curDate);
		}

		// create the data
		EuiLevel level = new EuiLevel();

		// set created in m_header and add to level
		m_header.setCreated(EventConstants.formatToString(curDate));
		level.setHeader(m_header);

		org.opennms.netmgt.xml.rtc.Category levelCat = new org.opennms.netmgt.xml.rtc.Category();

		// get a handle to data
		DataManager rtcDataMgr = (DataManager)RTCManager.getDataManager();
		synchronized(rtcDataMgr)
		{
			// category label
			levelCat.setCatlabel(rtcCat.getLabel());
			
			// value for this category
			levelCat.setCatvalue(rtcDataMgr.getValue(rtcCat.getLabel(), curTime, rWindow));
			
			// nodes in this category
			Iterator nodeIter = rtcCat.getNodes().iterator();
			while (nodeIter.hasNext())
			{
				Long rtcNodeid = (Long)nodeIter.next();
				long nodeID = rtcNodeid.longValue();

				Node levelNode = new Node();
				levelNode.setNodeid(nodeID);

				// value for this node for this category
				levelNode.setNodevalue(rtcDataMgr.getValue(nodeID, rtcCat.getLabel(), curTime, rWindow));

				// node service count
				levelNode.setNodesvccount(rtcDataMgr.getServiceCount(nodeID, rtcCat.getLabel()));

				// node service down count
				levelNode.setNodesvcdowncount(rtcDataMgr.getServiceDownCount(nodeID, rtcCat.getLabel()));
				// add the node
				levelCat.addNode(levelNode);
			}
				
		}

		// add category
		level.addCategory(levelCat);
		
		return level;
	}
}
