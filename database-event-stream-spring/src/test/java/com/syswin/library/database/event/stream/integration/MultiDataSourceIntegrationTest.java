/*
 * MIT License
 *
 * Copyright (c) 2019 Syswin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.syswin.library.database.event.stream.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

import com.syswin.library.database.event.stream.integration.containers.MysqlContainer;
import com.syswin.library.database.event.stream.integration.containers.ZookeeperContainer;
import java.util.List;
import java.util.Queue;
import javax.sql.DataSource;
import org.awaitility.Duration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.Network;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplication.class})
@ActiveProfiles("multi-source")
public class MultiDataSourceIntegrationTest {

  private static final Network NETWORK = Network.newNetwork();

  private static final MysqlContainer mysql0 = new MysqlContainer().withNetwork(NETWORK)
      .withNetworkAliases("mysql0-temail")
      .withEnv("MYSQL_DATABASE", "consistency0")
      .withEnv("MYSQL_USER", "syswin")
      .withEnv("MYSQL_PASSWORD", "password")
      .withEnv("MYSQL_ROOT_PASSWORD", "password0");

  private static final MysqlContainer mysql1 = new MysqlContainer().withNetwork(NETWORK)
      .withNetworkAliases("mysql1-temail")
      .withEnv("MYSQL_DATABASE", "consistency1")
      .withEnv("MYSQL_USER", "syswin")
      .withEnv("MYSQL_PASSWORD", "password")
      .withEnv("MYSQL_ROOT_PASSWORD", "password1");

  private static final ZookeeperContainer zookeeper = new ZookeeperContainer()
      .withNetwork(NETWORK)
      .withNetworkAliases("zookeeper-temail");

  @ClassRule
  public static final RuleChain RULES = RuleChain.outerRule(mysql0)
      .around(mysql1)
      .around(zookeeper);

  private final ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator();

  @Autowired
  private Queue<String> handledEvents;

  @Autowired
  private List<DataSource> dataSources;

  @BeforeClass
  public static void beforeClass() {
    System.setProperty("library.database.stream.multi.contexts[0].datasource.url",
        "jdbc:mysql://" + mysql0.getContainerIpAddress() + ":" + mysql0.getMappedPort(3306) + "/consistency0?useSSL=false");
    System.setProperty("library.database.stream.multi.contexts[0].datasource.username", "root");
    System.setProperty("library.database.stream.multi.contexts[0].datasource.password", "password0");
    System.setProperty("library.database.stream.multi.contexts[0].cluster.name", "consistency0");

    System.setProperty("library.database.stream.multi.contexts[1].datasource.url",
        "jdbc:mysql://" + mysql1.getContainerIpAddress() + ":" + mysql1.getMappedPort(3306) + "/consistency1?useSSL=false");
    System.setProperty("library.database.stream.multi.contexts[1].datasource.username", "root");
    System.setProperty("library.database.stream.multi.contexts[1].datasource.password", "password1");
    System.setProperty("library.database.stream.multi.contexts[1].cluster.name", "consistency1");

    System.setProperty("library.database.stream.zk.address",
        zookeeper.getContainerIpAddress() + ":" + zookeeper.getMappedPort(2181));
  }

  @AfterClass
  public static void afterClass() {
    System.clearProperty("library.database.stream.multi.contexts[0].url");
    System.clearProperty("library.database.stream.multi.contexts[0].datasource.username");
    System.clearProperty("library.database.stream.multi.contexts[0].datasource.password");
    System.clearProperty("library.database.stream.multi.contexts[0].cluster.name");

    System.clearProperty("library.database.stream.multi.contexts[1].url");
    System.clearProperty("library.database.stream.multi.contexts[1].datasource.username");
    System.clearProperty("library.database.stream.multi.contexts[1].datasource.password");
    System.clearProperty("library.database.stream.multi.contexts[1].cluster.name");
    System.clearProperty("library.database.stream.zk.address");
  }

  @Test
  public void streamEventsToMq() {
    databasePopulator.setScripts(new ClassPathResource("sql/schema.sql"), new ClassPathResource("data.sql"));
    DatabasePopulatorUtils.execute(databasePopulator, dataSources.get(0));

    databasePopulator.setScripts(new ClassPathResource("sql/schema.sql"), new ClassPathResource("data1.sql"));
    DatabasePopulatorUtils.execute(databasePopulator, dataSources.get(1));

    waitAtMost(Duration.FIVE_MINUTES).untilAsserted(() ->
        assertThat(handledEvents).containsExactlyInAnyOrder(
            "test1,bob,alice",
            "test2,jack,alice",
            "test3,bob,jack",
            "test4,john,bob",
            "test5,lucy,john",
            "test21,bob,alice",
            "test22,jack,alice",
            "test23,bob,jack",
            "test24,john,bob",
            "test25,lucy,john"
        ));
  }
}
