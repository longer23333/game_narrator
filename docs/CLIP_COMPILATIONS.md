# 切片合集与 V21 迁移

V21 新增 `clip_compilation` 和 `clip_compilation_item`。合集项通过 `task_id`、`clip_index` 引用已有项目切片，`position` 是持久化播放顺序。

接口：

- `POST /api/compilations` 创建合集。
- `POST /api/compilations/{id}/items` 加入项目切片。
- `PUT /api/compilations/{id}/order` 传入完整的 item ID 顺序并原子重排。
- `GET /api/compilations` 或 `GET /api/compilations/{id}` 查询。

删除视频任务或合集时，外键会级联删除关联项。需要回滚 V21 时，应先导出合集顺序，再删除 `clip_compilation_item`，最后删除 `clip_compilation`；不要修改已经执行过的 Flyway 文件。
