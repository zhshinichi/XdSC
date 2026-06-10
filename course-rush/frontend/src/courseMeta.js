// 课程展示元数据（学分/地点/学院/先修/考核/简介/编号）。
// 后端课程模型仅含 名称/教师/时间/容量，这些展示字段在前端按课程名维护。
export const COURSE_META = {
  '操作系统': { code: 'CS3001', credit: 4.0, location: '信远楼 II-201', college: '计算机科学与技术学院', pre: '数据结构与算法', exam: '闭卷考试', desc: '讲授进程与线程管理、内存管理、文件系统与设备管理的原理与实现，并结合实验理解操作系统内核机制。' },
  '计算机组成原理': { code: 'CS2002', credit: 3.5, location: '信远楼 II-305', college: '计算机科学与技术学院', pre: '数字逻辑电路', exam: '闭卷考试', desc: '围绕冯·诺依曼结构，讲解 CPU、存储层次、指令系统与总线，建立软硬件协同的整机概念。' },
  '数据结构与算法': { code: 'CS2001', credit: 4.0, location: '信远楼 I-118', college: '计算机科学与技术学院', pre: '程序设计基础', exam: '闭卷 + 上机', desc: '系统学习线性表、树、图等数据结构与排序、查找等经典算法，掌握时间空间复杂度分析。' },
  '计算机网络': { code: 'CS3003', credit: 3.5, location: '信远楼 II-210', college: '计算机科学与技术学院', pre: '操作系统', exam: '闭卷考试', desc: '讲解 TCP/IP 协议体系、路由与交换、可靠传输与典型应用层协议，理解互联网工作原理。' },
  '软件体系结构': { code: 'CS4001', credit: 3.0, location: '信远楼 I-402', college: '计算机科学与技术学院', pre: '软件工程', exam: '课程设计 + 答辩', desc: '讲授架构风格与设计模式、质量属性场景与 ATAM 评估方法，结合项目实践完成架构设计与评估。' },
  '人工智能导论': { code: 'AI2001', credit: 2.0, location: '北校区教学楼 A-101', college: '人工智能学院', pre: '无', exam: '课程论文', desc: '概览搜索与博弈、知识表示与推理、机器学习与智能体，建立人工智能整体认知框架。' },
  '机器学习实战': { code: 'AI3002', credit: 2.0, location: '北校区实验楼 B-203', college: '人工智能学院', pre: '高等数学', exam: '项目实践', desc: '讲解监督/无监督学习与模型评估，基于 scikit-learn 完成端到端的机器学习项目实战。' },
  'Python 程序设计': { code: 'CS1005', credit: 2.0, location: '信远楼 I-机房3', college: '计算机科学与技术学院', pre: '无', exam: '上机考试', desc: '从零掌握 Python 语法、数据处理与常用库，培养用脚本解决实际问题的能力。' },
  'Web 前端开发': { code: 'SE2003', credit: 2.0, location: '软件学院机房2', college: '软件学院', pre: '无', exam: '项目作品', desc: '学习 HTML/CSS/JavaScript 与现代前端框架，能够独立开发响应式 Web 应用界面。' },
  '数据库系统原理': { code: 'CS3004', credit: 3.0, location: '信远楼 II-118', college: '计算机科学与技术学院', pre: '数据结构与算法', exam: '闭卷 + 上机', desc: '讲授关系模型、SQL、索引、事务与并发控制，掌握数据库设计与优化方法。' },
  '云计算与容器技术': { code: 'CS4002', credit: 2.0, location: '信远楼 II-机房5', college: '计算机科学与技术学院', pre: '操作系统', exam: '项目实践', desc: '讲解虚拟化、Docker 与 Kubernetes，掌握容器化部署与微服务运维的核心技能。' },
  '区块链技术与应用': { code: 'IS3001', credit: 2.0, location: '北校区教学楼 C-305', college: '网络与信息安全学院', pre: '无', exam: '课程论文', desc: '讲解分布式账本、共识机制与智能合约，了解区块链在金融与存证等场景的应用。' },
  '网络空间安全导论': { code: 'IS2001', credit: 2.0, location: '北校区教学楼 C-201', college: '网络与信息安全学院', pre: '计算机网络', exam: '闭卷考试', desc: '介绍密码学基础、网络攻防与安全防护体系，建立网络空间安全的整体观念。' },
  '计算机视觉': { code: 'AI4001', credit: 2.0, location: '北校区实验楼 B-401', college: '人工智能学院', pre: '机器学习实战', exam: '项目实践', desc: '讲解图像处理、特征提取与深度视觉模型，完成目标检测/识别等视觉任务实践。' },
  '大数据分析': { code: 'CS4003', credit: 2.0, location: '信远楼 II-机房6', college: '计算机科学与技术学院', pre: '数据库系统原理', exam: '项目实践', desc: '讲解 Hadoop/Spark 生态与大规模数据处理，掌握数据采集、清洗与分析的完整流程。' },
  '如何成为世界首富': { code: 'GE9001', credit: 1.0, location: '南校区大学生活动中心报告厅', college: '通识教育中心', pre: '无', exam: '课程论文', desc: '一门轻松有趣的通识公选课，从财富观、复利与长期主义、创业与投资入门等角度，带你理性认识财富与人生规划。（仅作通识拓展，不构成任何投资建议）' },
}

const FALLBACK = { code: 'XD0000', credit: 2.0, location: '待定', college: '通识教育中心', pre: '无', exam: '考查', desc: '课程简介待补充。' }

export function getMeta(cleanName, id) {
  const m = COURSE_META[cleanName]
  if (m) return m
  return { ...FALLBACK, code: 'XD' + String(id).padStart(4, '0') }
}
