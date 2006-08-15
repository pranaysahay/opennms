//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2006 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
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
// OpenNMS Licensing       <license@opennms.org>
//     http://www.opennms.org/
//     http://www.opennms.com/
//

package org.opennms.netmgt.dao.ibatis;

import java.util.List;

import org.opennms.netmgt.dao.AggregateStatusDefinitionDao;
import org.opennms.netmgt.model.AggregateStatusDefinition;
import org.springframework.orm.ibatis.support.SqlMapClientDaoSupport;

public class SqlMapClientAggStatDefDao extends SqlMapClientDaoSupport implements
		AggregateStatusDefinitionDao {

	public void delete(AggregateStatusDefinition def) {
		getSqlMapClientTemplate().delete("AggStatDef.delete", new Integer(def.getId()));
	}

	public AggregateStatusDefinition find(String name) {
		return (AggregateStatusDefinition)getSqlMapClientTemplate().queryForObject("AggStatDef.getByName", name);

	}

	public AggregateStatusDefinition find(int id) {
		return (AggregateStatusDefinition)getSqlMapClientTemplate().queryForObject("AggStatDef.getByID", id);

	}

	public List getAll() {
		return getSqlMapClientTemplate().queryForList("AggStatDef.getAll", null);
	}

	public void save(AggregateStatusDefinition def) {
		if (def.getId() == 0) {
			insert(def);
		} else {
			update(def);
		}
	}

	public void insert(AggregateStatusDefinition def) {
		getSqlMapClientTemplate().insert("AggStatDef.insert", def);
	}

	public void update(AggregateStatusDefinition def) {
		getSqlMapClientTemplate().update("AggStatDef.update", def);
	}

}
