# FlinkSQL Plugin for IntelliJ IDEA

[Chinese Documentation](README_CN.md)

## Introduction
FlinkSQL-Plugin is an IntelliJ IDEA plugin developed 100% by cursor+claude3.5. It allows you to write FlinkSQL in IDEA and run it directly from the IDE. The plugin starts a local mini-cluster for execution.

## Use Cases
- Write FlinkSQL and run it directly in IDEA to improve development efficiency
- Debug FlinkSQL locally, e.g., debugging connectors, UDFs, etc.
- Enhance development efficiency when used in conjunction with other SQL plugins

## Operation Guide
![As shown in Figure 1](./doc/1.png)
1. Run FlinkSQL: Execute the FlinkSQL in the current file
2. Configure Flink VM parameters: Set VM parameters, e.g., -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005
3. Configure Flink Lib Directory: Set the Flink lib directory where all Flink dependency JARs are located. Ensure all relevant dependencies are in this directory before running

## Usage
1. Download the plugin and install it in IDEA. No need to restart IDEA
2. Create a new test.sql file, right-click and select Run FlinkSQL from the context menu

## IDEA Version Compatibility
- IDEA 2022+