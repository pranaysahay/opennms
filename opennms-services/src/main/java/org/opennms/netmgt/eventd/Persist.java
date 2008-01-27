//This file is part of the OpenNMS(R) Application.
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
// 2008 Jan 26: Dependency injection using setter injection instead of
//              constructor injection and implement InitializingBean.
//              Move some common setters and initializion into Persist.
//              Implement log method. - dj@opennms.org
// 2008 Jan 23: Use Java 5 generics, format code, wrap debug logs within an if
//              statement unless they are logging a plain String. - dj@opennms.org
//
// Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
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

package org.opennms.netmgt.eventd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.eventd.db.AutoAction;
import org.opennms.netmgt.eventd.db.Constants;
import org.opennms.netmgt.eventd.db.OperatorAction;
import org.opennms.netmgt.eventd.db.Parameter;
import org.opennms.netmgt.eventd.db.SnmpInfo;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Header;
import org.opennms.netmgt.xml.event.Operaction;
import org.springframework.util.Assert;

/**
 * EventWriter loads the information in each 'Event' into the database.
 * 
 * While loading mutiple values of the same element into a single DB column, the
 * mutiple values are delimited by MULTIPLE_VAL_DELIM.
 * 
 * When an element and its attribute are loaded into a single DB column, the
 * value and the attribute are separated by a DB_ATTRIB_DELIM.
 * 
 * When using delimiters to append values, if the values already have the
 * delimiter, the delimiter in the value is escaped as in URLs.
 * 
 * Values for the ' <parms>' block are loaded with each parm name and parm value
 * delimited with the NAME_VAL_DELIM.
 * 
 * @see org.opennms.netmgt.eventd.db.Constants#MULTIPLE_VAL_DELIM
 * @see org.opennms.netmgt.eventd.db.Constants#DB_ATTRIB_DELIM
 * @see org.opennms.netmgt.eventd.db.Constants#NAME_VAL_DELIM
 * 
 * @author <A HREF="mailto:david@opennms.org">David Hustace </A>
 * @author Sowmya Nataraj </A>
 * @author <A HREF="http://www.opennms.org">OpenNMS.org </A>
 * 
 * Changes:
 * 
 * - Alarm persisting added (many moons ago)
 * - Alarm persisting now removes oldest events by default.  Use "auto-clean" attribute
 *   in eventconf files.
 */
class Persist {
    // Field sizes in the events table
    private static final int EVENT_UEI_FIELD_SIZE = 256;

    private static final int EVENT_HOST_FIELD_SIZE = 256;

    private static final int EVENT_INTERFACE_FIELD_SIZE = 16;

    private static final int EVENT_DPNAME_FIELD_SIZE = 12;

    private static final int EVENT_SNMPHOST_FIELD_SIZE = 256;

    private static final int EVENT_SNMP_FIELD_SIZE = 256;

    private static final int EVENT_DESCR_FIELD_SIZE = 4000;

    private static final int EVENT_LOGGRP_FIELD_SIZE = 32;

    private static final int EVENT_LOGMSG_FIELD_SIZE = 256;

    private static final int EVENT_PATHOUTAGE_FIELD_SIZE = 1024;

    private static final int EVENT_CORRELATION_FIELD_SIZE = 1024;

    private static final int EVENT_OPERINSTRUCT_FIELD_SIZE = 1024;

    private static final int EVENT_AUTOACTION_FIELD_SIZE = 256;

    private static final int EVENT_OPERACTION_FIELD_SIZE = 256;

    private static final int EVENT_OPERACTION_MENU_FIELD_SIZE = 64;

//    private static final int EVENT_NOTIFICATION_FIELD_SIZE = 128;

    private static final int EVENT_TTICKET_FIELD_SIZE = 128;

    private static final int EVENT_FORWARD_FIELD_SIZE = 256;

    private static final int EVENT_MOUSEOVERTEXT_FIELD_SIZE = 64;

    private static final int EVENT_ACKUSER_FIELD_SIZE = 256;

    private static final int EVENT_SOURCE_FIELD_SIZE = 128;
    
    private static final int EVENT_X733_ALARMTYPE_SIZE = 31;

    /**
     * The character to put in if the log or display is to be set to yes
     */
    private static char MSG_YES = 'Y';

    /**
     * The character to put in if the log or display is to be set to no
     */
    private static char MSG_NO = 'N';

    /**
     * The database connection
     */
    protected Connection m_dsConn;

    /**
     * SQL statement to get hostname for an ip from the ipinterface table
     */
    protected PreparedStatement m_getHostNameStmt;

    /**
     * SQL statement to get next event id from sequence
     */
    protected PreparedStatement m_getNextIdStmt;

    /**
     * SQL statement to get insert an event into the db
     */
    protected PreparedStatement m_insStmt;
    protected PreparedStatement m_reductionQuery;
    protected PreparedStatement m_upDateStmt;

    protected PreparedStatement m_updateEventStmt;

    private EventdServiceManager m_eventdServiceManager;

    private DataSource m_dataSource;

    /**
     * Sets the statement up for a String value.
     * 
     * @param stmt
     *            The statement to add the value to.
     * @param ndx
     *            The ndx for the value.
     * @param value
     *            The value to add to the statement.
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the value to the
     *                statement.
     */
    private void set(PreparedStatement stmt, int ndx, String value) throws SQLException {
        if (value == null || value.length() == 0) {
            stmt.setNull(ndx, Types.VARCHAR);
        } else {
            stmt.setString(ndx, value);
        }
    }

    /**
     * Sets the statement up for an integer type. If the integer type is less
     * than zero, then it is set to null!
     * 
     * @param stmt
     *            The statement to add the value to.
     * @param ndx
     *            The ndx for the value.
     * @param value
     *            The value to add to the statement.
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the value to the
     *                statement.
     */
    private void set(PreparedStatement stmt, int ndx, int value) throws SQLException {
        if (value < 0) {
            stmt.setNull(ndx, Types.INTEGER);
        } else {
            stmt.setInt(ndx, value);
        }
    }

    /**
     * Sets the statement up for a timestamp type.
     * 
     * @param stmt
     *            The statement to add the value to.
     * @param ndx
     *            The ndx for the value.
     * @param value
     *            The value to add to the statement.
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the value to the
     *                statement.
     */
    private void set(PreparedStatement stmt, int ndx, Timestamp value) throws SQLException {
        if (value == null) {
            stmt.setNull(ndx, Types.TIMESTAMP);
        } else {
            stmt.setTimestamp(ndx, value);
        }
    }

    /**
     * Sets the statement up for a character value.
     * 
     * @param stmt
     *            The statement to add the value to.
     * @param ndx
     *            The ndx for the value.
     * @param value
     *            The value to add to the statement.
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the value to the
     *                statement.
     */
    private void set(PreparedStatement stmt, int ndx, char value) throws SQLException {
        stmt.setString(ndx, String.valueOf(value));
    }

    /**
     * This method is used to convert the service name into a service id. It
     * first looks up the information from a service map of Eventd and if no
     * match is found, by performing a lookup in the database. If the conversion
     * is successful then the corresponding integer identifier will be returned
     * to the caller.
     * 
     * @param name
     *            The name of the service
     * 
     * @return The integer identifier for the service name.
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error accessing the stored data or
     *                the SQL text is malformed. This will also be thrown if the
     *                result cannot be obtained.
     * 
     * @see EventdConstants#SQL_DB_SVCNAME_TO_SVCID
     * 
     */
    private int getServiceID(String name) throws SQLException {
        return m_eventdServiceManager.getServiceId(name);
    }

    /**
     * This method is used to convert the event host into a hostname id by
     * performing a lookup in the database. If the conversion is successful then
     * the corresponding hosname will be returned to the caller.
     * 
     * @param hostip
     *            The event host
     * 
     * @return The hostname
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error accessing the stored data or
     *                the SQL text is malformed.
     * 
     * @see EventdConstants#SQL_DB_HOSTIP_TO_HOSTNAME
     * 
     */
    private String getHostName(String hostip) throws SQLException {
        // talk to the database and get the identifer
        String hostname = hostip;

        m_getHostNameStmt.setString(1, hostip);
        ResultSet rset = null;
        
        try {
            rset = m_getHostNameStmt.executeQuery();
            if (rset.next()) {
                hostname = rset.getString(1);
            }
        } catch (SQLException e) {
            throw e;
        } finally {
            rset.close();
        }

        // hostname can be null - if it is, return the ip
        if (hostname == null) {
            hostname = hostip;
        }

        return hostname;
    }
    
    public void insertOrUpdateAlarm(Header eventHeader, Event event) throws SQLException {
        int alarmId = isReductionNeeded(eventHeader, event);
        if (alarmId != -1) {
            if (log().isDebugEnabled()) {
                log().debug("Reducing event for: " + event.getDbid() + ": " + event.getUei());
            }
            updateAlarm(eventHeader, event, alarmId);
            
            // This removes all previous events that have been reduced.
            if (log().isDebugEnabled()) {
                log().debug("insertOrUpdate: auto-clean is: " + event.getAlarmData().getAutoClean());
            }
            if (event.getAlarmData().getAutoClean() == true) {
                log().debug("insertOrUpdate: deleting previous events");
                cleanPreviousEvents(alarmId, event.getDbid());
            }
            
        } else {
            if (log().isDebugEnabled()) {
                log().debug("Not reducing event for: " + event.getDbid() + ": " + event.getUei());
            }
            insertAlarm(eventHeader, event);
        }
    }
    
    /*
     * Don't throw from here, deal with any SQL exception and don't effect updating an alarm.
     */
    private void cleanPreviousEvents(int alarmId, int eventId) {
        PreparedStatement stmt = null;
        try {
            stmt = m_dsConn.prepareStatement("DELETE FROM events WHERE alarmId = ? AND eventId != ?");
            stmt.setInt(1, alarmId);
            stmt.setInt(2, eventId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log().error("cleanPreviousEvents: Couldn't remove old events: " + e, e);
        }

        try {
            stmt.close();
        } catch (SQLException e) {
            log().error("cleanPreviousEvents: Couldn't close statement: " + e, e);
        }
    }

    protected Category log() {
        return ThreadCategory.getInstance(getClass());
    }

    private int isReductionNeeded(Header eventHeader, Event event) throws SQLException {
        if (log().isDebugEnabled()) {
            log().debug("Persist.isReductionNeeded: reductionKey: " + event.getAlarmData().getReductionKey());
        }

        m_reductionQuery.setString(1, event.getAlarmData().getReductionKey());

        ResultSet rs = null;
        int alarmId;
        try {
            rs = m_reductionQuery.executeQuery();
            alarmId = -1;
            while (rs.next()) {
                alarmId = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw e;
        } finally {
            rs.close();
        }
        return alarmId;
    }
    
    private void updateAlarm(Header eventHeader, Event event, int alarmId) throws SQLException {
        m_upDateStmt.setInt(1, event.getDbid());
        m_upDateStmt.setTimestamp(2, getEventTime(event));
        set(m_upDateStmt, 3, Constants.format(event.getLogmsg().getContent(), EVENT_LOGMSG_FIELD_SIZE));
        m_upDateStmt.setString(4, event.getAlarmData().getReductionKey());

        if (log().isDebugEnabled()) {
            log().debug("Persist.updateAlarm: reducing event: " + event.getDbid() +  " into alarm");
        }
        
        m_upDateStmt.executeUpdate();

        m_updateEventStmt.setInt(1, alarmId);
        m_updateEventStmt.setInt(2, event.getDbid());
        m_updateEventStmt.executeUpdate();
    }
    
    /**
     * Insert values into the ALARMS table
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the event to the
     *                database.
     * @exception java.lang.NullPointerException
     *                Thrown if a required resource cannot be found in the
     *                properties file.
     */
    private void insertAlarm(Header eventHeader, Event event) throws SQLException {
        int alarmID = -1;
        alarmID = getNextId();
        if (log().isDebugEnabled()) {
            log().debug("AlarmWriter: DBID: " + alarmID);
        }

        //Column 1, alarmId
        m_insStmt.setInt(1, alarmID);
        
        //Column 2, eventUie
        m_insStmt.setString(2, Constants.format(event.getUei(), EVENT_UEI_FIELD_SIZE));
        
        //Column 3, dpName
        m_insStmt.setString(3, (eventHeader != null) ? Constants.format(eventHeader.getDpName(), EVENT_DPNAME_FIELD_SIZE) : "undefined");
        
        // Column 4, nodeID
        int nodeid = (int) event.getNodeid();
        m_insStmt.setObject(4, event.hasNodeid() ? new Integer(nodeid) : null);
        
        // Column 5, ipaddr
        m_insStmt.setString(5, event.getInterface());
        
        //Column 6, serviceId
        //
        // convert the service name to a service id
        //
        int svcId = -1;
        if (event.getService() != null) {
            try {
                svcId = getServiceID(event.getService());
            } catch (SQLException e) {
                log().warn("insertAlarm: Error converting service name \"" + event.getService() + "\" to an integer identifier, storing -1: " + e, e);
            }
        }
        m_insStmt.setObject(6, (svcId == -1 ? null : new Integer(svcId)));

        //Column 7, reductionKey
        m_insStmt.setString(7, event.getAlarmData().getReductionKey());
        
        //Column 8, alarmType
        m_insStmt.setInt(8, event.getAlarmData().getAlarmType());
        
        //Column 9, counter
        m_insStmt.setInt(9, 1);
        
        //Column 10, serverity
        set(m_insStmt, 10, Constants.getSeverity(event.getSeverity()));

        //Column 11, lastEventId
        m_insStmt.setInt(11, event.getDbid());
        
        //Column 12, firstEventTime
        //Column 13, lastEventTime
        Timestamp eventTime = getEventTime(event);
        m_insStmt.setTimestamp(12, eventTime);
        m_insStmt.setTimestamp(13, eventTime);
        
        //Column 14, description
        set(m_insStmt, 14, Constants.format(event.getDescr(), EVENT_DESCR_FIELD_SIZE));

        //Column 15, logMsg
        if (event.getLogmsg() != null) {
            // set log message
            set(m_insStmt, 15, Constants.format(event.getLogmsg().getContent(), EVENT_LOGMSG_FIELD_SIZE));
        } else {
            m_insStmt.setNull(15, Types.VARCHAR);
        }

        //Column 16, operInstruct
        set(m_insStmt, 16, Constants.format(event.getOperinstruct(), EVENT_OPERINSTRUCT_FIELD_SIZE));
        
        //Column 17, tticketId
        //Column 18, tticketState
        if (event.getTticket() != null) {
            set(m_insStmt, 17, Constants.format(event.getTticket().getContent(), EVENT_TTICKET_FIELD_SIZE));
            int ttstate = 0;
            if (event.getTticket().getState().equals("on")) {
                ttstate = 1;
            }
            set(m_insStmt, 18, ttstate);
        } else {
            m_insStmt.setNull(17, Types.VARCHAR);
            m_insStmt.setNull(18, Types.INTEGER);
        }

        //Column 19, mouseOverText
        set(m_insStmt, 19, Constants.format(event.getMouseovertext(), EVENT_MOUSEOVERTEXT_FIELD_SIZE));

        //Column 20, suppressedUntil
        set(m_insStmt, 20, eventTime);
        
        //Column 21, suppressedUser
        m_insStmt.setString(21, null);
        
        //Column 22, suppressedTime
        set(m_insStmt, 22, eventTime);
        
        //Column 23, alarmAckUser
        m_insStmt.setString(23, null);
        
        //Column 24, alarmAckTime
        m_insStmt.setTimestamp(24, null);
        
        //Column 25, clearUie
        //Column 26, x733AlarmType
        //Column 27, x733ProbableCause
        //Column 28, clearKey
        if (event.getAlarmData() == null) {
            m_insStmt.setString(25, null);
            m_insStmt.setString(26, null);
            m_insStmt.setInt(27, -1);
            m_insStmt.setString(28, null);
        } else {
            m_insStmt.setString(25, Constants.format(event.getAlarmData().getClearUei(), EVENT_UEI_FIELD_SIZE));
            m_insStmt.setString(26, Constants.format(event.getAlarmData().getX733AlarmType(), EVENT_X733_ALARMTYPE_SIZE));
            set(m_insStmt, 27, event.getAlarmData().getX733ProbableCause());
            set(m_insStmt, 28, event.getAlarmData().getClearKey());
        }
        
        if (log().isDebugEnabled()) {
            log().debug("m_insStmt is: " + m_insStmt.toString());
        }
        
        m_insStmt.executeUpdate();
        
        m_updateEventStmt.setInt(1, alarmID);
        m_updateEventStmt.setInt(2, event.getDbid());
        m_updateEventStmt.executeUpdate();

        if (log().isDebugEnabled()) {
            log().debug("SUCCESSFULLY added " + event.getUei() + " related  data into the ALARMS table");
        }
   
    }


    /**
     * Insert values into the EVENTS table
     * 
     * @exception java.sql.SQLException
     *                Thrown if there is an error adding the event to the
     *                database.
     * @exception java.lang.NullPointerException
     *                Thrown if a required resource cannot be found in the
     *                properties file.
     */
    protected void insertEvent(Header eventHeader, Event event) throws SQLException {
        // Execute the statement to get the next event id
        int eventID = getNextId();

        if (log().isDebugEnabled()) {
            log().debug("EventWriter: DBID: " + eventID);
        }

        synchronized (event) {
            event.setDbid(eventID);
        }

        // Set up the sql information now

        // eventID
        m_insStmt.setInt(1, eventID);

        // eventUEI
        m_insStmt.setString(2, Constants.format(event.getUei(), EVENT_UEI_FIELD_SIZE));

        // nodeID
        int nodeid = (int) event.getNodeid();
        set(m_insStmt, 3, event.hasNodeid() ? nodeid : -1);

        // eventTime
        m_insStmt.setTimestamp(4, getEventTime(event));
        
        // Resolve the event host to a hostname using the ipInterface table
        String hostname = getEventHost(event);

        // eventHost
        set(m_insStmt, 5, Constants.format(hostname, EVENT_HOST_FIELD_SIZE));

        // ipAddr
        set(m_insStmt, 6, Constants.format(event.getInterface(), EVENT_INTERFACE_FIELD_SIZE));

        // eventDpName
        m_insStmt.setString(7, (eventHeader != null) ? Constants.format(eventHeader.getDpName(), EVENT_DPNAME_FIELD_SIZE) : "undefined");

        // eventSnmpHost
        set(m_insStmt, 8, Constants.format(event.getSnmphost(), EVENT_SNMPHOST_FIELD_SIZE));

        // service identifier - convert the service name to a service id
        set(m_insStmt, 9, getEventServiceId(event));

        // eventSnmp
        if (event.getSnmp() != null) {
            m_insStmt.setString(10, SnmpInfo.format(event.getSnmp(), EVENT_SNMP_FIELD_SIZE));
        } else {
            m_insStmt.setNull(10, Types.VARCHAR);
        }

        // eventParms

        // Replace any null bytes with a space, otherwise postgres will complain about encoding in UNICODE 
        String parametersString=(event.getParms() != null) ? Parameter.format(event.getParms()) : null;
        if (parametersString != null) {
            parametersString=parametersString.replace((char)0, ' ');
        }
        
        set(m_insStmt, 11, parametersString);

        // grab the ifIndex out of the parms if it is defined   
        if (event.getIfIndex() != null) {
            if (event.getParms() != null) {
                Parameter.format(event.getParms());
            }
        }

        // eventCreateTime
        Timestamp eventCreateTime = new Timestamp(System.currentTimeMillis());
        m_insStmt.setTimestamp(12, eventCreateTime);

        // eventDescr
        set(m_insStmt, 13, Constants.format(event.getDescr(), EVENT_DESCR_FIELD_SIZE));

        // eventLoggroup
        set(m_insStmt, 14, (event.getLoggroupCount() > 0) ? Constants.format(event.getLoggroup(), EVENT_LOGGRP_FIELD_SIZE) : null);

        // eventLogMsg
        // eventLog
        // eventDisplay
        if (event.getLogmsg() != null) {
            // set log message
            set(m_insStmt, 15, Constants.format(event.getLogmsg().getContent(), EVENT_LOGMSG_FIELD_SIZE));
            String logdest = event.getLogmsg().getDest();
            if (logdest.equals("logndisplay")) {
                // if 'logndisplay' set both log and display column to yes
                set(m_insStmt, 16, MSG_YES);
                set(m_insStmt, 17, MSG_YES);
            } else if (logdest.equals("logonly")) {
                // if 'logonly' set log column to true
                set(m_insStmt, 16, MSG_YES);
                set(m_insStmt, 17, MSG_NO);
            } else if (logdest.equals("displayonly")) {
                // if 'displayonly' set display column to true
                set(m_insStmt, 16, MSG_NO);
                set(m_insStmt, 17, MSG_YES);
            } else if (logdest.equals("suppress")) {
                // if 'suppress' set both log and display to false
                set(m_insStmt, 16, MSG_NO);
                set(m_insStmt, 17, MSG_NO);
            }
        } else {
            m_insStmt.setNull(15, Types.VARCHAR);

            /*
             * If this is an event that had no match in the event conf
             * mark it as to be logged and displayed so that there
             * are no events that slip through the system
             * without the user knowing about them
             */
            set(m_insStmt, 17, MSG_YES);
        }

        // eventSeverity
        set(m_insStmt, 18, Constants.getSeverity(event.getSeverity()));

        // eventPathOutage
        set(m_insStmt, 19, (event.getPathoutage() != null) ? Constants.format(event.getPathoutage(), EVENT_PATHOUTAGE_FIELD_SIZE) : null);

        // eventCorrelation
        set(m_insStmt, 20, (event.getCorrelation() != null) ? org.opennms.netmgt.eventd.db.Correlation.format(event.getCorrelation(), EVENT_CORRELATION_FIELD_SIZE) : null);

        // eventSuppressedCount
        m_insStmt.setNull(21, Types.INTEGER);

        // eventOperInstruct
        set(m_insStmt, 22, Constants.format(event.getOperinstruct(), EVENT_OPERINSTRUCT_FIELD_SIZE));

        // eventAutoAction
        set(m_insStmt, 23, (event.getAutoactionCount() > 0) ? AutoAction.format(event.getAutoaction(), EVENT_AUTOACTION_FIELD_SIZE) : null);

        // eventOperAction / eventOperActionMenuText
        if (event.getOperactionCount() > 0) {
            List<Operaction> a = new ArrayList<Operaction>();
            List<String> b = new ArrayList<String>();

            for (Operaction eoa : event.getOperactionCollection()) {
                a.add(eoa);
                b.add(eoa.getMenutext());
            }

            set(m_insStmt, 24, OperatorAction.format(a, EVENT_OPERACTION_FIELD_SIZE));
            set(m_insStmt, 25, Constants.format(b, EVENT_OPERACTION_MENU_FIELD_SIZE));
        } else {
            m_insStmt.setNull(24, Types.VARCHAR);
            m_insStmt.setNull(25, Types.VARCHAR);
        }

        // eventNotification, this column no longer needed
        m_insStmt.setNull(26, Types.VARCHAR);

        // eventTroubleTicket / eventTroubleTicket state
        if (event.getTticket() != null) {
            set(m_insStmt, 27, Constants.format(event.getTticket().getContent(), EVENT_TTICKET_FIELD_SIZE));
            int ttstate = 0;
            if (event.getTticket().getState().equals("on")) {
                ttstate = 1;
            }

            set(m_insStmt, 28, ttstate);
        } else {
            m_insStmt.setNull(27, Types.VARCHAR);
            m_insStmt.setNull(28, Types.INTEGER);
        }

        // eventForward
        set(m_insStmt, 29, (event.getForwardCount() > 0) ? org.opennms.netmgt.eventd.db.Forward.format(event.getForward(), EVENT_FORWARD_FIELD_SIZE) : null);

        // event mouseOverText
        set(m_insStmt, 30, Constants.format(event.getMouseovertext(), EVENT_MOUSEOVERTEXT_FIELD_SIZE));

        // eventAckUser
        if (event.getAutoacknowledge() != null && event.getAutoacknowledge().getState().equals("on")) {
            set(m_insStmt, 31, Constants.format(event.getAutoacknowledge().getContent(), EVENT_ACKUSER_FIELD_SIZE));

            // eventAckTime - if autoacknowledge is present,
            // set time to event create time
            set(m_insStmt, 32, eventCreateTime);
        } else {
            m_insStmt.setNull(31, Types.INTEGER);
            m_insStmt.setNull(32, Types.TIMESTAMP);
        }

        // eventSource
        set(m_insStmt, 33, Constants.format(event.getSource(), EVENT_SOURCE_FIELD_SIZE));

        // execute
        m_insStmt.executeUpdate();

        if (log().isDebugEnabled()) {
            log().debug("SUCCESSFULLY added " + event.getUei() + " related  data into the EVENTS table");
        }
    }

    /**
     * @param event
     * @param log
     * @return
     */
    private int getEventServiceId(Event event) {
        int svcId = -1;
        if (event.getService() != null) {
            try {
                svcId = getServiceID(event.getService());
            } catch (SQLException e) {
                log().warn("EventWriter.add: Error converting service name \"" + event.getService() + "\" to an integer identifier, storing -1: e" + e, e);
            }
        }
        return svcId;
    }

    /**
     * @param event
     * @return
     */
    private String getEventHost(Event event) {
        String hostname = event.getHost();
        if (hostname != null) {
            try {
                hostname = getHostName(hostname);
            } catch (SQLException sqlE) {
                // hostname can be null - so use the IP
                hostname = event.getHost();
            }
        }
        return hostname;
    }

    /**
     * @param event
     * @param log
     * @return
     */
    private Timestamp getEventTime(Event event) {
        try {
            return new Timestamp(EventConstants.parseToDate(event.getTime()).getTime());
        } catch (ParseException e) {
            log().warn("Failed to convert time " + event.getTime() + " to Timestamp, setting current time instead.  Exception: " + e, e);
            return new Timestamp(System.currentTimeMillis());
        }
    }

    public Persist() {
    }

    /**
     * Close all the connection statements
     */
    public void close() {
        try {
            m_dsConn.close();
        } catch (SQLException e) {
            log().warn("SQLException while closing database connection", e);
        }
    }
    
    private int getNextId() throws SQLException {
        int id;
        // Get the next id from sequence specified in
        ResultSet rs = null;
        try {
            rs = m_getNextIdStmt.executeQuery();
            rs.next();
            id = rs.getInt(1);
        } catch (SQLException e) {
            throw e;
        } finally {
            if (rs != null) {
                rs.close();
            }
        }
        rs = null;
        return id;
    }

    public EventdServiceManager getEventdServiceManager() {
        return m_eventdServiceManager;
    }

    public void setEventdServiceManager(EventdServiceManager eventdServiceManager) {
        m_eventdServiceManager = eventdServiceManager;
    }

    public DataSource getDataSource() {
        return m_dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        m_dataSource = dataSource;
    }

    public void afterPropertiesSet() throws SQLException {
        Assert.state(m_eventdServiceManager != null, "property eventdServiceManager must be set");
        Assert.state(m_dataSource != null, "property dataSource must be set");

        // Get a database connection
        m_dsConn = m_dataSource.getConnection();
}
}
