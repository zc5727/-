-- 会员智能运营中枢 —— 建表脚本
--
-- 放在业务库（gym_management）里，与智能体库（fitness_ai）物理分离。
-- 全部使用 IF NOT EXISTS / INSERT IGNORE，重复执行无副作用，可以安全地
-- 在已跑起来的环境里直接执行。
--
-- 设计取向：这里存的是"算出来的结论"，不是原始流水。
-- 会员、签到、充值这些原始表保持不动，业务系统的既有逻辑零改动；
-- 运营中枢只做只读计算 + 结果落库，因此可以随时删掉重算，不会影响账目。

-- ----------------------------
-- 1. 会员分层（RFM）
-- ----------------------------
DROP TABLE IF EXISTS member_segment;
CREATE TABLE member_segment (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  member_no    INT         NOT NULL COMMENT '会员编号',
  recency_days INT         NULL     COMMENT '距最近一次签到天数',
  frequency    INT         NULL     COMMENT '近 90 天签到次数',
  monetary     DOUBLE      NULL     COMMENT '近 90 天充值金额',
  segment      VARCHAR(24) NOT NULL COMMENT 'HIGH_VALUE / GROWING / SLEEPING / CHURN_RISK',
  score         DOUBLE     NULL     COMMENT '综合得分，用于同层内排序',
  computed_at  DATETIME    NOT NULL COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member (member_no),
  KEY idx_segment (segment),
  KEY idx_computed (computed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员分层（RFM）';

-- ----------------------------
-- 2. 流失预警信号
-- ----------------------------
DROP TABLE IF EXISTS member_churn_signal;
CREATE TABLE member_churn_signal (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  member_no   INT         NOT NULL COMMENT '会员编号',
  signal_type VARCHAR(32) NOT NULL COMMENT 'NO_CHECKIN / LOW_BALANCE / NO_RECHARGE',
  weight      DOUBLE      NOT NULL COMMENT '信号权重，越大越危险',
  detail      VARCHAR(300) NULL    COMMENT '人类可读的信号说明',
  detected_at DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_member (member_no, detected_at),
  KEY idx_type (signal_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员流失预警信号';

-- ------------------------
-- 3. 运营日快照
-- ------------------------
DROP TABLE IF EXISTS ops_daily_snapshot;
CREATE TABLE ops_daily_snapshot (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  snapshot_date    DATE   NOT NULL COMMENT '快照日期',
  new_members      INT    NOT NULL DEFAULT 0 COMMENT '当日新增会员',
  checkin_count    INT    NOT NULL DEFAULT 0 COMMENT '当日签到人次',
  recharge_amount  DOUBLE NOT NULL DEFAULT 0 COMMENT '当日充值金额',
  course_seat_rate DOUBLE NULL     COMMENT '课程上座率 %',
  created_at       DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营日快照';

-- ----------------------------
-- 4. 器材维护工单
-- ----------------------------
DROP TABLE IF EXISTS equipment_maintenance;
CREATE TABLE equipment_maintenance (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  equipment_no INT         NOT NULL COMMENT '器材编号',
  plan_type    VARCHAR(24) NOT NULL COMMENT 'ROUTINE 例行保养 / REPAIR 故障维修',
  title        VARCHAR(200) NOT NULL COMMENT '工单标题',
  status       VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
               COMMENT 'PLANNED 待派工 / ASSIGNED 已派工 / DONE 已完成 / VERIFIED 已验收',
  assignee     VARCHAR(64) NULL COMMENT '负责人',
  cost         DOUBLE      NULL COMMENT '维修成本',
  planned_date DATE        NULL COMMENT '计划日期',
  finished_at  DATETIME    NULL COMMENT '完成时间',
  created_at   DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_equipment (equipment_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材维护工单';

-- ----------------------------
-- 5. 工单流转日志
-- ----------------------------
DROP TABLE IF EXISTS equipment_maintenance_log;
CREATE TABLE equipment_maintenance_log (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  order_id   BIGINT      NOT NULL COMMENT '工单号',
  action     VARCHAR(24) NOT NULL COMMENT 'CREATE / ASSIGN / FINISH / VERIFY',
  operator   VARCHAR(64) NULL,
  note       VARCHAR(500) NULL,
  created_at DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材工单流转日志';
