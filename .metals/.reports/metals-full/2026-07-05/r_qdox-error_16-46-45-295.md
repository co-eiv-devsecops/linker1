error id: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java
file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[9,1]

error in qdox parser
file content:
```java
offset: 216
uri: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java
text:
```scala
import io.javalin.Javalin;
import java.sql.*;

import linker.LinkRepository;
import linker.LinkService;
import linker.config.FeatureFlags;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;
i@@mpo

public class Main {

    FeatureFlags featureFlags = new FeatureFlags();

    public static void main(String[] args) throws Exception {
        var dbPath = System.getenv().getOrDefault("LINKER_DB_PATH", "linker1.db");
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }

        int port = Integer.parseInt(
            System.getenv().getOrDefault("LINKER_PORT", "8080")
        );

        var app = Javalin.create().start(port);

        var repository = new LinkRepository(conn);
        var service = new LinkService(repository);

        StaticRoutes.register(app, featureFlags);
        LinkRoutes.register(app, service);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }));
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1583)
```
#### Short summary: 

QDox parse error in file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java