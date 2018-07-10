# testredisreplicator

### 本例通过redisreplicator实现redis单实例同步，无其他附加机制

### 同步方案待实现功能及需要思考的问题

* 工程中实现同步数据日志
* 错误日志
* 与kafka或其他mq产品结合实现消息堆积
* 中断后的数据补偿机制
* 双中心时如何避免消息循环
* 针对集群的方案

另外的方案是携程开源的X-pipe（https://github.com/ctripcorp/x-pipe），架构比较重，学习曲线应该也比较高


目前支持redis4.0以上版本数据复制的开源产品我找到的有两个，一个是redis-replicator（https://github.com/leonchen83/redis-replicator），另外的方案是携程开源的X-pipe（https://github.com/ctripcorp/x-pipe）。
redis-replicator本质上是一个redis命令复制工具，独立jar包。要实现同步功能需要自行开发。
X-pipe架构比较重，学习曲线和维护成本应该也比较高，目前还没时间做验证。周末这两天主要是验证了一下redis-replicator。
验证过程如下
* 建两个redis单实例
* 用redis-replicator构建一个可执行的jar包（为了快速验证，原地址和目的地址硬编码）
* 启动两个实例以及打包好的jar文件
* 实例1添加数据，实例2同步添加
源码位置
https://github.com/jiashiwen/testredisreplicator

基本的单实例复制没太大问题，但是要形成部署方案还有不少坑要填。
一个比较成熟的夸IDC缓存复制方案至少要支持消息中间件进行数据同步，这样可以避免当网络抖动或其他因素造成网路断路时消息堆积的问题；另外redis集群方案比较多，支持集群复制要看具体的集群方案来制定相应的数据传输规则。我把目前能想到的开发及工程方面的问题做了一下总结：
* 工程中实现同步数据日志
* 错误日志
* 与kafka或其他mq产品结合实现消息堆积
* 中断后的数据补偿机制
* 双中心时如何避免消息循环
* 针对集群的方案
另外源码中注释不多且文档不是很详细，仅提供了部分事例，熟悉源码及其机制也是一块时间成本    

* 双中心如何避免消息循环
  采用记录同步日志的方式，hashmap记录同步的kv，每次当有新的插如或更新时，检查command及kv如果操作完全一致，则忽略该条更新，缓存定时清理

