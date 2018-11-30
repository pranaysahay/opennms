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

package org.opennms.netmgt.topologies.service.api;


import java.util.HashMap;
import java.util.Map;

public class OnmsTopologyVertex extends OnmsTopologyAbstractRef implements OnmsTopologyRef {


    public static OnmsTopologyVertex create(String id,String label,String address, String iconKey) throws OnmsTopologyException {
        if (id == null) {
            throw new OnmsTopologyException("id is null, cannot create vertex");
        }
        return new OnmsTopologyVertex(id,label,address,iconKey);
    }
    
    private final String m_label;
    private final String m_address;
    private final String m_iconKey;
    private Map<String,String> m_attributes = new HashMap<String,String>();

    private OnmsTopologyVertex(String id, String label, String address,String iconKey) {
        super(id);
        m_label=label;
        m_address=address;
        m_iconKey=iconKey;
    }

    public Map<String, String> getAttributes() {
        return m_attributes;
    }


    public void setAttributes(Map<String, String> attributes) {
        m_attributes = attributes;
    }


    public String getLabel() {
        return m_label;
    }


    public String getAddress() {
        return m_address;
    }


    public String getIconKey() {
        return m_iconKey;
    }

}
