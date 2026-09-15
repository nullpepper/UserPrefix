# 自动同步说明（sync-upstream.yml）

本目录下的 `sync-upstream.yml` 负责把**上游（父仓库）**的默认分支同步进本 fork，
而**不会**动你自己的功能分支（如 `feat/*`）。

- 上游：`CarmJos/UserPrefix`
- 本 fork：`nullpepper/UserPrefix`
- 同步目标：默认分支 `master`

## 它什么时候跑

| 触发方式 | 说明 |
| --- | --- |
| 定时 | 每天一次（UTC 04:17 = 北京时间 12:17），检查上游是否发了新的 release |
| 手动 | Actions 页面 → Sync with upstream → Run workflow（可勾选 `force` 强制同步） |
| 外部信号 | 别的系统对 `upstream-release` 发 `repository_dispatch` |

> GitHub 只在本仓库自己发 release 时才发 `release` 事件，**上游发 release 不会通知 fork**。
> 所以「上游一发 release 就同步」只能靠这里的定时轮询来实现，最多延迟一天。

## 为什么用服务端接口而不是本地推送

默认分支的同步走 GitHub 的 `merge-upstream` 接口（网页「Sync fork」按钮背后的接口），
在服务端完成合并。

原因：`GITHUB_TOKEN` **没有 `workflows` 权限**，只要这次同步涉及
`.github/workflows/` 下的文件改动，本地 `git push` 一定会被拒：

```
refusing to allow a GitHub App to create or update workflow `.github/workflows/xxx.yml`
without `workflows` permission
```

这个限制无法通过 `permissions:` 打开，只有 PAT / OAuth token 的 `workflow` scope 可以。
服务端接口不受此限制，所以默认分支必须走它。

非默认分支（手动指定 `target_branch` 时）只能本地合并再推送，
因此上游若改了同名 workflow 文件会推送失败——错误信息里会说明这一点。

## 冲突时会怎样

同步**不会**强行覆盖你的代码：

- 能快进就快进；
- 你自己在目标分支上有提交时，会产生一个 merge 提交；
- 一旦冲突，任务**失败停下**，并打印冲突文件和你领先上游的提交，远端保持不动。

处理办法：把你自己的改动挪到独立分支（如 `feat/xxx`），让默认分支保持是上游的副本，
再重跑本 workflow。

## 两个已知的维护坑

1. **定时任务会在 60 天无仓库活动后自动停用**（GitHub 对 scheduled workflow 的限制）。
   若本 fork 长期没有其他活动，定时同步会停摆。恢复办法：进 Actions 页面手动
   Run workflow 一次，或随便推一个提交，即可重新激活。

2. **如果你自己改了 `.github/workflows/sync-upstream.yml` 并推上去**，
   而之后上游的提交也碰到这个文件，服务端合并会冲突。此时按上面的办法处理，
   或改用不同的文件名规避。

## 它不会做的事

- 不重建 GitHub Release 记录（上游的 Release 不会出现在本 fork 的 Releases 页面；
  但 `sync_tags` 打开时会把上游的 tag 同步过来）。
- 不强制覆盖、不改写历史、不动你的其他分支。
- 不把上游的其他分支（如 `ver/*`）同步过来。