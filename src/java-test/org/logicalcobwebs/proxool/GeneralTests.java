/*
 * This software is released under the Apache Software Licence. See
 * package.html for details. The latest version is available at
 * http://proxool.sourceforge.net
 */
package org.logicalcobwebs.proxool;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.Iterator;

/**
 * Various tests
 *
 * @version $Revision: 1.21 $, $Date: 2002/12/03 12:25:05 $
 * @author billhorsman
 * @author $Author: billhorsman $ (current maintainer)
 */
public class GeneralTests extends TestCase {

    private static final Log LOG = LogFactory.getLog(GeneralTests.class);

    private static final String TEST_TABLE = "test";

    public GeneralTests(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        AllTests.globalSetup();
        try {
            TestHelper.createTable(TEST_TABLE);
        } catch (Exception e) {
            LOG.debug("Problem creating table", e);
        }
    }

    protected void tearDown() throws Exception {
        TestHelper.dropTable(TEST_TABLE);
        AllTests.globalTeardown();
    }

    /**
     * Can we refer to the same pool by either the complete URL or the alias?
     */
    public void testAlias() throws SQLException, ClassNotFoundException {

        String alias = "alias";

        // Register pool
        {
            String url = TestHelper.getFullUrl(alias);
            Connection c = TestHelper.getProxoolConnection(url);
            TestHelper.insertRow(c, TEST_TABLE);
        }

        // Get it back by url
        {
            String url = TestHelper.getFullUrl(alias);
            Connection c = TestHelper.getProxoolConnection(url);
            TestHelper.insertRow(c, TEST_TABLE);
        }

        // Get it back by name
        {
            String url = TestHelper.getSimpleUrl(alias);
            Connection c = TestHelper.getProxoolConnection(url);
            TestHelper.insertRow(c, TEST_TABLE);
        }

        ConnectionPoolStatisticsIF connectionPoolStatistics = ProxoolFacade.getConnectionPoolStatistics(alias);

        // If the above calls all used the same pool then it should have served exactly 3 connections.s
        assertEquals(3L, connectionPoolStatistics.getConnectionsServedCount());

    }

    private void templateTest() {

        String testName = "template";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    /**
     * Can we update a pool definition by passing a new Properties object?
     */
    public void testUpdate() throws SQLException, ClassNotFoundException {

        String testName = "template";
        ProxoolAdapter adapter = null;
        Connection c = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "1");
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            // Open a connection. Just for the hell of it
            c = adapter.getConnection();
            adapter.closeConnection(c);

            assertEquals("maximumConnectionCount", 1, ProxoolFacade.getConnectionPoolDefinition(alias).getMaximumConnectionCount());

            // Update using facade
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "2");
            ProxoolFacade.updateConnectionPool(alias, info);
            assertEquals("maximumConnectionCount", 2, ProxoolFacade.getConnectionPoolDefinition(alias).getMaximumConnectionCount());

            // Now do it on the fly
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "3");
            c = DriverManager.getConnection(adapter.getFullUrl(), info);
            c.close();
            assertEquals("maximumConnectionCount", 3, ProxoolFacade.getConnectionPoolDefinition(alias).getMaximumConnectionCount());

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    public void testMaximumActiveTime() {

        String testName = "maximumActiveTime";
        String threadName = Thread.currentThread().getName();
        Thread.currentThread().setName(testName);
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            info.setProperty(ProxoolConstants.MAXIMUM_ACTIVE_TIME_PROPERTY, "5000");
            info.setProperty(ProxoolConstants.HOUSE_KEEPING_SLEEP_TIME_PROPERTY, "5000");
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            assertEquals("Shuoldn't be any active connections yet", ProxoolFacade.getConnectionPoolStatistics(alias).getActiveConnectionCount(), 0);

            Connection connection = adapter.getConnection();

            assertEquals("We just opened 1 connection", ProxoolFacade.getConnectionPoolStatistics(alias).getActiveConnectionCount(), 1);

            long start = System.currentTimeMillis();
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                LOG.debug("Awoken.");
            }

            long elapsed = System.currentTimeMillis() - start;
            assertTrue("Connection has not been closed after " + elapsed + " milliseconds as expected", connection.isClosed());

            assertEquals("Expected the connection to be inactive", ProxoolFacade.getConnectionPoolStatistics(alias).getActiveConnectionCount(), 0);

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
            Thread.currentThread().setName(threadName);
        }

    }

    public void testConnectionListener() {

        String testName = "connectionListener";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            info.setProperty(ProxoolConstants.VERBOSE_PROPERTY, "true");
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            ProxoolFacade.setConnectionListener(alias, new ConnectionListenerIF() {

                public void onBirth(Connection connection) throws SQLException {
                    LOG.debug("onBirth");
                }

                public void onDeath(Connection connection) throws SQLException {
                    LOG.debug("onDeath");
                }

                public void onExecute(String command, long elapsedTime) {
                    LOG.debug("onExecute: " + command + " (" + elapsedTime + ")");
                }

                public void onFail(String command, Exception exception) {
                    LOG.debug("onFail", exception);
                }

            });

            Connection connection = adapter.getConnection();

            TestHelper.execute(connection, "insert into test values(1)");

            connection.close();

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    /**
     * If we ask for more simultaneous connections then we have allowed we should gracefully
     * refuse them.
     */
    public void testMaximumConnectionCount() throws SQLException {

        String testName = "maximumConnectionCount";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            adapter = new ProxoolAdapter(alias);
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "2");
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            adapter.getConnection();
            adapter.getConnection();

            try {
                adapter.getConnection();
                fail("Didn't expect to get third connection");
            } catch (SQLException e) {
                // Good. We expected to not get the third
            }

            assertEquals("activeConnectionCount", 2, ProxoolFacade.getConnectionPoolStatistics(alias).getActiveConnectionCount());

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    /**
     * If we ask for more simultaneous connections then we have allowed we should gracefully
     * refuse them.
     */
    public void testConnectionInfo() throws SQLException {

        String testName = "connectionInfo";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            adapter = new ProxoolAdapter(alias);
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "3");
            info.setProperty(ProxoolConstants.PROTOTYPE_COUNT_PROPERTY, "0");
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            Connection c1 = adapter.getConnection();
            assertEquals("connectionInfo count", 1, ProxoolFacade.getConnectionInfos(alias).size());

            Connection c2 = adapter.getConnection();
            assertEquals("connectionInfo count", 2, ProxoolFacade.getConnectionInfos(alias).size());

            Connection c3 = adapter.getConnection();
            c3.close();
            assertEquals("connectionInfo count", 3, ProxoolFacade.getConnectionInfos(alias).size());

            Iterator i =  ProxoolFacade.getConnectionInfos(alias).iterator();
            ConnectionInfoIF ci1 = (ConnectionInfoIF) i.next();
            ConnectionInfoIF ci2 = (ConnectionInfoIF) i.next();
            ConnectionInfoIF ci3 = (ConnectionInfoIF) i.next();

            assertEquals("#1 status", ConnectionInfoIF.STATUS_ACTIVE, ci1.getStatus());
            assertEquals("#2 status", ConnectionInfoIF.STATUS_ACTIVE, ci2.getStatus());
            assertEquals("#3 status", ConnectionInfoIF.STATUS_AVAILABLE, ci3.getStatus());

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    /**
     * Test that spare connections are made as we run out of them
     */
    public void testPrototyping() {

        String testName = "maximumActiveTime";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            info.setProperty(ProxoolConstants.MINIMUM_CONNECTION_COUNT_PROPERTY, "0");
            info.setProperty(ProxoolConstants.MAXIMUM_CONNECTION_COUNT_PROPERTY, "5");
            info.setProperty(ProxoolConstants.PROTOTYPE_COUNT_PROPERTY, "2");
            info.setProperty(ProxoolConstants.HOUSE_KEEPING_SLEEP_TIME_PROPERTY, "5000");
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            Connection[] connections = new Connection[6];
            ConnectionPoolStatisticsIF cps;

            Thread.sleep(10000);
            cps = ProxoolFacade.getConnectionPoolStatistics(alias);
            assertEquals("activeConnectionCount", 0, cps.getActiveConnectionCount());
            assertEquals("availableConnectionCount", 2, cps.getAvailableConnectionCount());

            connections[0] = adapter.getConnection();

            Thread.sleep(10000);
            cps = ProxoolFacade.getConnectionPoolStatistics(alias);
            assertEquals("activeConnectionCount", 1, cps.getActiveConnectionCount());
            assertEquals("availableConnectionCount", 2, cps.getAvailableConnectionCount());

            connections[1] = adapter.getConnection();
            connections[2] = adapter.getConnection();
            connections[3] = adapter.getConnection();

            Thread.sleep(10000);
            cps = ProxoolFacade.getConnectionPoolStatistics(alias);
            assertEquals("activeConnectionCount", 4, cps.getActiveConnectionCount());
            assertEquals("availableConnectionCount", 1, cps.getAvailableConnectionCount());

            // Clean up
            for (int i = 0; i < connections.length; i++) {
                if (connections[i] != null && !connections[i].isClosed()) {
                    adapter.closeConnection(connections[i]);
                }

            }

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }

    /**
     * Can we have multiple pools?
     */
    public void testMultiple() throws SQLException, ClassNotFoundException {

        String testName = "template";
        ProxoolAdapter adapter1 = null;
        ProxoolAdapter adapter2 = null;
        try {
            Properties info = TestHelper.buildProperties();
            String alias1 = testName + "1";
            adapter1 = new ProxoolAdapter(alias1);
            adapter1.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);
            String alias2 = testName + "2";
            adapter2 = new ProxoolAdapter(alias2);
            adapter2.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            // Open 2 connections on #1
            adapter1.getConnection().close();
            adapter1.getConnection().close();

            // Open 1 connection on #2
            adapter2.getConnection().close();

            assertEquals("connectionsServedCount #1", 2L, ProxoolFacade.getConnectionPoolStatistics(alias1).getConnectionsServedCount());
            assertEquals("connectionsServedCount #2", 1L, ProxoolFacade.getConnectionPoolStatistics(alias2).getConnectionsServedCount());

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter1.tearDown();
            adapter2.tearDown();
        }
    }

    public void testFatalSqlException() {

        String testName = "fatalSqlException";
        ProxoolAdapter adapter = null;
        try {
            String alias = testName;
            Properties info = TestHelper.buildProperties();
            info.setProperty(ProxoolConstants.FATAL_SQL_EXCEPTION_PROPERTY, "not found");
            adapter = new ProxoolAdapter(alias);
            adapter.setup(TestHelper.HYPERSONIC_DRIVER, TestHelper.HYPERSONIC_URL, info);

            Connection c = adapter.getConnection();
            Statement s = c.createStatement();
            try {
                s.execute("drop table foo");
            } catch (SQLException e) {
                // Expected exception (foo doesn't exist)
                LOG.debug("Excepted exception", e);
            }

            c.close();
            
            assertEquals("availableConnectionCount", 0L, ProxoolFacade.getConnectionPoolStatistics(alias).getAvailableConnectionCount());

        } catch (Exception e) {
            LOG.error("Whilst performing " + testName, e);
            fail(e.getMessage());
        } finally {
            adapter.tearDown();
        }

    }


}

/*
 Revision history:
 $Log: GeneralTests.java,v $
 Revision 1.21  2002/12/03 12:25:05  billhorsman
 new fatal sql exception test

 Revision 1.20  2002/11/14 16:19:02  billhorsman
 test thread name

 Revision 1.19  2002/11/13 20:23:58  billhorsman
 improved tests

 Revision 1.18  2002/11/13 18:28:43  billhorsman
 checkstyle

 Revision 1.17  2002/11/13 18:04:22  billhorsman
 new prototyping test

 Revision 1.16  2002/11/09 16:09:06  billhorsman
 checkstyle

 Revision 1.15  2002/11/09 15:50:15  billhorsman
 new trace property and better doc

 Revision 1.14  2002/11/07 19:08:55  billhorsman
 Fixed up tests a bit

 Revision 1.13  2002/11/07 18:53:19  billhorsman
 Slight improvement to setup

 Revision 1.12  2002/11/02 13:57:34  billhorsman
 checkstyle

 Revision 1.11  2002/10/29 23:17:38  billhorsman
 Cleaned up SQL stuff

 Revision 1.10  2002/10/29 08:54:04  billhorsman
 fixed testUpdate (wasn't closing a connection)

 Revision 1.9  2002/10/27 12:03:33  billhorsman
 clear up of tests

 Revision 1.8  2002/10/25 10:41:07  billhorsman
 draft changes to test globalSetup

 Revision 1.7  2002/10/23 21:04:54  billhorsman
 checkstyle fixes (reduced max line width and lenient naming convention

 Revision 1.6  2002/10/19 17:00:38  billhorsman
 added performance test, and created TestHelper to make it all simpler

 Revision 1.5  2002/09/19 10:34:47  billhorsman
 new testInfo test

 Revision 1.4  2002/09/19 10:06:39  billhorsman
 improved load test

 Revision 1.3  2002/09/18 13:48:56  billhorsman
 checkstyle and doc

 Revision 1.2  2002/09/17 22:44:19  billhorsman
 improved tests

 Revision 1.1.1.1  2002/09/13 08:14:24  billhorsman
 new

 Revision 1.5  2002/08/24 20:07:48  billhorsman
 renamed tests

 Revision 1.4  2002/08/24 19:44:13  billhorsman
 fixes for logging

 Revision 1.3  2002/07/10 16:14:47  billhorsman
 widespread layout changes and move constants into ProxoolConstants

 Revision 1.2  2002/07/10 10:04:03  billhorsman
 fixed compile bug. silly me :(

 Revision 1.1  2002/07/04 09:01:53  billhorsman
 More tests

 Revision 1.2  2002/07/02 11:19:08  billhorsman
 layout code and imports

 Revision 1.1  2002/07/02 09:10:35  billhorsman
 Junit tests

*/
