# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


### Added

#### Adding a function for rebalancing a resource #332

### Changed

#### Allow setting of proxy user/zone in IRODSAccount #338

Convenience method for creating IRODSAccount proxy user settings

#### Add tests/doc and clarify function of proxy users #339

Added functional tests and clarified setting of proxyUser and proxyZone in IRODSAccount.



#### When invoking a rule via jargon the microservice msiDataObjGet doesn't end #337

Added overhead code for client-side rule actions to send an oprComplete after a parallel get
operation. This prevents 'stuck' rules. Fix for user-reported issue.
