# lykke-waves-blockchain-scanner
Waves Blockchain Scanner Module for Lykke Exchange.

This module scans the Waves blockchain and creates indexes with the necessary information for the operation of the API service.

# Build

This project uses [sbt](https://www.scala-sbt.org/) for building:

```
sbt clean debian:packageBin
```

After that, the .deb package will be available in the `target` project folder.

# Installation

Just install the .deb package and start the service.

# Configuration

For this moment there are no any configuration allowed, it requires MongoDB installed at `mongodb://127.0.0.1:27017` and uses db `lykke-waves`. It's just a daemon what scans the blocks and creates the indexes for addresses from observables lists (for from/to/balances data).
