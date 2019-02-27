package com.syswin.library.database.event.stream.mysql;

import static com.github.shyiko.mysql.binlog.event.EventType.EXT_WRITE_ROWS;
import static com.github.shyiko.mysql.binlog.event.EventType.TABLE_MAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.github.shyiko.mysql.binlog.event.Event;
import com.syswin.library.database.event.stream.DbEventStreamConnectionException;
import com.syswin.library.database.event.stream.DbEventStreamEndOfLifeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.mockito.Mockito;

public class BinlogStreamStatefulTaskTest {

  private final List<Throwable> exceptions = new ArrayList<>();
  private final Consumer<Throwable> throwableConsumer = exceptions::add;
  private final Consumer<Event> eventHandler = event -> {};

  private final MysqlBinLogStream binLogStream = Mockito.mock(MysqlBinLogStream.class);

  private final BinlogStreamStatefulTask task = new BinlogStreamStatefulTask(binLogStream, eventHandler, TABLE_MAP, EXT_WRITE_ROWS);

  @Test
  public void startUnderlyingStream() throws IOException {

    task.start(throwableConsumer);

    assertThat(exceptions).isEmpty();
    verify(binLogStream).start(eventHandler, throwableConsumer, TABLE_MAP, EXT_WRITE_ROWS);
  }

  @Test
  public void stopUnderlyingStream() {

    task.stop();

    assertThat(exceptions).isEmpty();
    verify(binLogStream).stop();
  }

  @Test
  public void handleErrorOnException() throws IOException {
    IOException exception1 = new IOException("oops");
    DbEventStreamConnectionException exception2 = new DbEventStreamConnectionException("oops", exception1);
    doThrow(exception1, exception2).when(binLogStream).start(eventHandler, throwableConsumer, TABLE_MAP, EXT_WRITE_ROWS);

    task.start(throwableConsumer);
    task.start(throwableConsumer);

    assertThat(exceptions).containsOnly(exception1, exception2);
  }

  @Test(expected = DbEventStreamEndOfLifeException.class)
  public void blowsUpOnDbEventStreamEndOfLifeException() throws IOException {
    IOException exception1 = new IOException("oops");
    DbEventStreamEndOfLifeException exception2 = new DbEventStreamEndOfLifeException(exception1);
    doThrow(exception2).when(binLogStream).start(eventHandler, throwableConsumer, TABLE_MAP, EXT_WRITE_ROWS);

    task.start(throwableConsumer);
  }
}
