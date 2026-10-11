# Step 1: local ZooKeeper

## Start and inspect

Start the single ZooKeeper server:

```sh
docker compose up -d
docker compose ps
docker compose logs -f zookeeper
```

Open the ZooKeeper command-line client inside the container:

```sh
docker compose exec zookeeper zkCli.sh
```

Try a few read and write operations at the `zkCli` prompt:

```text
ls /
create /hello world
get /hello
set /hello "hello again"
get /hello
delete /hello
```

Exit with `quit`. Stop the server with `docker compose down`; start it again
and check that its ZooKeeper data remains in the named volumes. To remove the
server and its stored data after experiments, use `docker compose down -v`.

## What is running?

ZooKeeper stores a small, hierarchical data tree. Each node in that tree is a
**znode**. A znode can hold bytes and can have child znodes, so `/hello` is a
path in ZooKeeper's namespace, not a file on the host.

The published port `2181` is the client port. The command-line client above,
and later our Curator client, use it to send requests. It is separate from the
peer ports used for communication among members of an ensemble. This first
setup has no peers, so it cannot demonstrate leader election, quorum, or
survival of a server failure.

The named Docker volumes keep ZooKeeper's data directory and transaction log
outside the container's writable layer. A normal `docker compose down` removes
the container but keeps those volumes; `docker compose down -v` deletes them.

## Service discovery contract (initial sketch)

We'll start with a registry keyed by a logical service name, such as
`payments`. A running instance will publish an address, port, and optional
metadata. A discovery client will get the current instances for that service
and refresh its view as instances join or leave. Later, we'll decide the exact
record format and API after observing how ZooKeeper sessions and ephemeral
znodes behave.

The key design question to keep in view: what should happen to an instance's
registration when its process becomes unreachable? ZooKeeper sessions and
ephemeral znodes will give us a mechanism, and experiments will help us
understand its timing and limits.
