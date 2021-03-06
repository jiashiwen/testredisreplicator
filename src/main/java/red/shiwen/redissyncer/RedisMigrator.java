package red.shiwen.redissyncer;

import com.beust.jcommander.JCommander;
import com.moilioncircle.redis.replicator.*;
import com.moilioncircle.redis.replicator.cmd.Command;
import com.moilioncircle.redis.replicator.cmd.CommandListener;
import com.moilioncircle.redis.replicator.cmd.CommandName;
import com.moilioncircle.redis.replicator.cmd.impl.DefaultCommand;
import com.moilioncircle.redis.replicator.cmd.parser.DefaultCommandParser;
import com.moilioncircle.redis.replicator.cmd.parser.PingParser;
import com.moilioncircle.redis.replicator.rdb.RdbListener;
import com.moilioncircle.redis.replicator.rdb.datatype.DB;
import com.moilioncircle.redis.replicator.rdb.datatype.KeyValuePair;
import com.moilioncircle.redis.replicator.rdb.dump.DumpRdbVisitor;
import com.moilioncircle.redis.replicator.rdb.dump.datatype.DumpKeyValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import red.shiwen.redissyncer.entity.ArgsSettingEntity;
import red.shiwen.redissyncer.util.FileUtil;
import redis.clients.jedis.Client;
import redis.clients.jedis.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static redis.clients.jedis.Protocol.Command.AUTH;
import static redis.clients.jedis.Protocol.Command.RESTORE;
import static redis.clients.jedis.Protocol.Command.SELECT;
import static redis.clients.jedis.Protocol.toByteArray;

public class RedisMigrator {


    static final String loggerpath = "log4j2.yml";
    static Logger logger;
//    static final Logger logger = LogManager.getLogger("transfor");
    static final FileUtil fileutil = new FileUtil();


    public static void main(String[] args) throws IOException, URISyntaxException {

//        System.out.println(logconfigfile.getAbsoluteFile());


        ArgsSettingEntity argssetting = new ArgsSettingEntity();
        JCommander jc = new JCommander();
        jc.setProgramName("java -jar redisreplicator.jar");
        jc.addObject(argssetting);

        /*
         * 解析输入参数
         */
        try {
            jc.parse(args);
        } catch (Exception e) {
            jc.usage();
            System.exit(0);
        }

        if (argssetting.isHelp()) {
            jc.usage();
            System.exit(0);
        }

        sync(argssetting.getSource(), argssetting.getTarget());

//        sync("redis://118.184.217.153:6379","redis://118.184.216.157:6379" );

    }

    public static void sync(String sourceUri, String targetUri) throws IOException, URISyntaxException {

        File logconfigfile = new File(loggerpath);

        ConfigurationSource source;
        if (logconfigfile.exists()) {
            source = new ConfigurationSource(new FileInputStream(logconfigfile));
            Configurator.initialize(null, source);
            System.out.println(logconfigfile.getAbsoluteFile() + " exiest");
        }

        logger = LogManager.getLogger("transfor");

        RedisURI suri = new RedisURI(sourceUri);
        RedisURI turi = new RedisURI(targetUri);
        final ExampleClient target = new ExampleClient(turi.getHost(), turi.getPort());
        Configuration tconfig = Configuration.valueOf(turi);

        //获取password
        if (tconfig.getAuthPassword() != null) {
            Object auth = target.send(AUTH, tconfig.getAuthPassword().getBytes());
//            System.out.println("AUTH:" + auth);
        }
        final AtomicInteger dbnum = new AtomicInteger(-1);
        Replicator r = dress(new RedisReplicator(suri));


        r.addRdbListener(new RdbListener.Adaptor() {
            @Override
            public void handle(Replicator replicator, KeyValuePair<?> kv) {
                StringBuffer info = new StringBuffer();
                if (!(kv instanceof DumpKeyValuePair)) return;
                // Step1: select db
                DB db = kv.getDb();
                int index;
                if (db != null && (index = (int) db.getDbNumber()) != dbnum.get()) {
                    target.send(SELECT, toByteArray(index));
                    dbnum.set(index);
                    info.append("SELECT:");
                    info.append(index);
                    logger.info(info);
                }

                info.setLength(0);
                // Step2: restore dump data
                DumpKeyValuePair mkv = (DumpKeyValuePair) kv;
                if (mkv.getExpiredMs() == null) {
                    Object r = target.restore(mkv.getRawKey(), 0L, mkv.getValue(), true);
                    info.append(mkv.getKey());
                    info.append("->");
                    info.append(r.toString());
                    logger.info(info);
                } else {
                    long ms = mkv.getExpiredMs() - System.currentTimeMillis();
                    if (ms <= 0) return;
                    Object r = target.restore(mkv.getRawKey(), ms, mkv.getValue(), true);
                    info.append(mkv.getKey());
                    info.append("->");
                    info.append(r.toString());
                    logger.info(info);
                }
            }
        });
        r.addCommandListener(new CommandListener() {
            @Override
            public void handle(Replicator replicator, Command command) {
                if (!(command instanceof DefaultCommand)) return;
                // Step3: sync aof command
                DefaultCommand dc = (DefaultCommand) command;
                Object r = target.send(dc.getCommand(), dc.getArgs());
                StringBuffer info = new StringBuffer();
                info.append(new String(dc.getCommand()));
                info.append(":");

                for (byte[] arg : dc.getArgs()) {
                    info.append("[");
                    info.append(new String(arg));
                    info.append("]");
                }

                info.append("->");
                info.append(r);
                logger.info(info);
            }
        });
        r.addCloseListener(new CloseListener() {
            @Override
            public void handle(Replicator replicator) {
                target.close();
            }
        });
        r.open();

    }

    public static Replicator dress(Replicator r) {

        r.getConfiguration();
        r.setRdbVisitor(new DumpRdbVisitor(r));
        r.addCommandParser(CommandName.name("PING"), new PingParser());
        r.addCommandParser(CommandName.name("APPEND"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SETEX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("MSET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("DEL"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SADD"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("HMSET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("HSET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LSET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("EXPIRE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("EXPIREAT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("GETSET"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("HSETNX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("MSETNX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PSETEX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SETNX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SETRANGE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("HDEL"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LPOP"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LPUSH"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LPUSHX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LRem"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RPOP"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RPUSH"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RPUSHX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZREM"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RENAME"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("INCR"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("DECR"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("INCRBY"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("DECRBY"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PERSIST"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SELECT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("FLUSHALL"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("FLUSHDB"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("HINCRBY"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZINCRBY"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("MOVE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SMOVE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PFADD"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PFCOUNT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PFMERGE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SDIFFSTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SINTERSTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SUNIONSTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZADD"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZINTERSTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZUNIONSTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("BRPOPLPUSH"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LINSERT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RENAMENX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RESTORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PEXPIRE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PEXPIREAT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("GEOADD"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("EVAL"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("EVALSHA"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SCRIPT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("PUBLISH"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("BITOP"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("BITFIELD"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SETBIT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SREM"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("UNLINK"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SWAPDB"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("MULTI"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("EXEC"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZREMRANGEBYSCORE"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZREMRANGEBYRANK"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("ZREMRANGEBYLEX"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("LTRIM"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("SORT"), new DefaultCommandParser());
        r.addCommandParser(CommandName.name("RPOPLPUSH"), new DefaultCommandParser());
        return r;
    }


    public static class ExampleClient extends Client {

        public ExampleClient(final String host, final int port) {
            super(host, port);
        }

        public Object send(Protocol.Command cmd, final byte[]... args) {
            sendCommand(cmd, args);
            Object r = getOne();
            if (r instanceof byte[]) {
                return new String((byte[]) r, UTF_8);
            } else {
                return r;
            }
        }

        public Object send(final byte[] cmd, final byte[]... args) {
            return send(Protocol.Command.valueOf(new String(cmd, UTF_8).toUpperCase()), args);
        }

        public Object restore(byte[] key, long expired, byte[] dumped, boolean replace) {
            if (!replace) {
                return send(RESTORE, key, toByteArray(expired), dumped);
            } else {
                return send(RESTORE, key, toByteArray(expired), dumped, "REPLACE".getBytes());
            }
        }
    }

}
