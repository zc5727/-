import request from "@/utils/request";

// 管理员登录
export const getAdminPassword = params =>{
    return request({
        url:'/getAdminPassword',
        method:'post',
        params:params
    })
}

// 会员登录
export const getMemberPassword = params =>{
    return request({
        url:'/getMemberPassword',
        method:'post',
        params:params
    })
}
/*------------------------------------------------*/
// <查找所有会员信息
export const getAllMember = params =>{
    return request({
        url:'/getAllMember',
        method:'get',
        params:params
    })
}

// <查找所有会员信息
export const memberExport = params =>{
    return request({
        url:'/memberExport',
        method:'get',
        params:params
    })
}


//新增会员
export const addMember = params =>{
    return request({
        url:'/addMember',
        method:'post',
        params:params
    })
}

/*删除会员信息*/
export const deleteMember = params =>{
    return request({
        url:'/deleteMember',
        method:'delete',
        params:params
    })
}

/*修改会员信息*/
export const updateMember = params =>{
    return request({
        url:'/updateMember',
        method:'post',
        params:params
    })
}

/*修改会员信息*/
export const updateMemberByMemberNo = params =>{
    return request({
        url:'/updateMemberByMemberNo',
        method:'post',
        params:params
    })
}

/*查找会员表数据总条数*/
export const totalMember = params =>{
    return request({
        url:'/totalMember',
        method:'post',
        params:params
    })
}

// 模糊查询会员表
export const getByKeywordMember = params =>{
    return request({
        url:'/getByKeywordMember',
        method:'post',
        params:params
    })
}

// 模糊查询查找会员表数据总条数
export const totalMemberFuzzy = params =>{
    return request({
        url:'/totalMemberFuzzy',
        method:'post',
        params:params
    })
}

//*----------------------------------------------*/

// 查找所有员工信息
export const getAllEmployee = params =>{
    return request({
        url:'/getAllEmployee',
        method:'get',
        params:params
    })
}
//新增员工
export const addEmployee = params =>{
    return request({
        url:'/addEmployee',
        method:'post',
        params:params
    })
}

/*删除员工信息*/
export const deleteEmployee = params =>{
    return request({
        url:'/deleteEmployee',
        method:'delete',
        params:params
    })
}

/*修改员工信息*/
export const updateEmployee = params =>{
    return request({
        url:'/updateEmployee',
        method:'post',
        params:params
    })
}

/*查找员工表数据总条数*/
export const totalEmployee = params =>{
    return request({
        url:'/totalEmployee',
        method:'post',
        params:params
    })
}

// 模糊查询员工表
export const getByKeywordEmployee = params =>{
    return request({
        url:'/getByKeywordEmployee',
        method:'post',
        params:params
    })
}

// 模糊查询查找员工表数据总条数
export const totalEmployeeFuzzy = params =>{
    return request({
        url:'/totalEmployeeFuzzy',
        method:'post',
        params:params
    })
}

//*----------------------------------------------*/

// 查找所有器材信息
export const getAllEquipment = params =>{
    return request({
        url:'/getAllEquipment',
        method:'get',
        params:params
    })
}
//新增员工
export const addEquipment = params =>{
    return request({
        url:'/addEquipment',
        method:'post',
        params:params
    })
}

/*删除员工信息*/
export const deleteEquipment = params =>{
    return request({
        url:'/deleteEquipment',
        method:'delete',
        params:params
    })
}

/*修改员工信息*/
export const updateEquipment = params =>{
    return request({
        url:'/updateEquipment',
        method:'post',
        params:params
    })
}

/*查找员工表数据总条数*/
export const totalEquipment = params =>{
    return request({
        url:'/totalEquipment',
        method:'post',
        params:params
    })
}

// 模糊查询员工表
export const getByKeywordEquipment = params =>{
    return request({
        url:'/getByKeywordEquipment',
        method:'post',
        params:params
    })
}


// 查询所有教练
export const getAllCoach = params =>{
    return request({
        url:'/getAllCoach',
        method:'get',
        params:params
    })
}



// 查询所有经理
export const getAllManager = params =>{
    return request({
        url:'/getAllManager',
        method:'get',
        params:params
    })
}


// 模糊查询查找员工表数据总条数
export const totalEquipmentFuzzy = params =>{
    return request({
        url:'/totalEquipmentFuzzy',
        method:'post',
        params:params
    })
}


//*----------------------------------------------*/

// 查找所有器材信息
export const getAllCourse = params =>{
    return request({
        url:'/getAllCourse',
        method:'get',
        params:params
    })
}

// 查找所有器材信息
export const getAllCourseRegister = params =>{
    return request({
        url:'/getAllCourseRegister',
        method:'get',
        params:params
    })
}


//新增课程
export const addCourse = params =>{
    return request({
        url:'/addCourse',
        method:'post',
        data:params
    })
}

/*删除课程信息*/
export const deleteCourse = params =>{
    return request({
        url:'/deleteCourse',
        method:'delete',
        params:params
    })
}

/*修改课程信息*/
export const updateCourse = params =>{
    return request({
        url:'/updateCourse',
        method:'post',
        data:params
    })
}

/*查找课程表数据总条数*/
export const totalCourse = params =>{
    return request({
        url:'/totalCourse',
        method:'post',
        params:params
    })
}

// 模糊查询课程表
export const getByKeywordCourse = params =>{
    return request({
        url:'/getByKeywordCourse',
        method:'post',
        params:params
    })
}

// 模糊查询查找课程表数据总条数
export const totalCourseFuzzy = params =>{
    return request({
        url:'/totalCourseFuzzy',
        method:'post',
        params:params
    })
}

/*----------------------------------*/

// 查询注报名表数据总条数
export const getAllRegister = params =>{
    return request({
        url:'/getAllRegister',
        method:'get',
        params:params
    })
}

// 查询注报名表数据总条数
export const getRegisterByMemberNo = params =>{
    return request({
        url:'/getRegisterByMemberNo',
        method:'get',
        params:params
    })
}

//报名表课程
export const addRegister = params =>{
    return request({
        url:'/addRegister',
        method:'post',
        params:params
    })
}

/*删除报名信息*/
export const deleteRegister = params =>{
    return request({
        url:'/deleteRegister',
        method:'delete',
        params:params
    })
}

/*修改报名信息*/
export const updateRegister = params =>{
    return request({
        url:'/updateRegister',
        method:'post',
        params:params
    })
}

/*查找报名数据总条数*/
export const totalRegister = params =>{
    return request({
        url:'/totalRegister',
        method:'post',
        params:params
    })
}

// 模糊查询报名表
export const getByKeywordRegister = params =>{
    return request({
        url:'/getByKeywordRegister',
        method:'post',
        params:params
    })
}

// 模糊查询查找课程表数据总条数
export const totalRegisterFuzzy = params =>{
    return request({
        url:'/totalRegisterFuzzy',
        method:'post',
        params:params
    })
}

// ------------------------------------------


export const getRechargeByMemberNo = params =>{
    return request({
        url:'/getRechargeByMemberNo',
        method:'get',
        params:params
    })
}

export const getMemberIntegral = params =>{
    return request({
        url:'/getMemberIntegral',
        method:'get',
        params:params
    })
}

export const getMemberChange = params =>{
    return request({
        url:'/getMemberChange',
        method:'get',
        params:params
    })
}

export const getTotalMoney = params =>{
    return request({
        url:'/getTotalMoney',
        method:'get',
        params:params
    })
}

export const addRechargeByMemberNo = params =>{
    return request({
        url:'/addRechargeByMemberNo',
        method:'post',
        params:params
    })
}
//更新member表的余额
export const updateMemberChange = params =>{
    return request({
        url:'/updateMemberChange',
        method:'post',
        params:params
    })
}

//购买会员后更新member表的积分
export const updateMemberIntegral = params =>{
    return request({
        url:'/updateMemberIntegral',
        method:'post',
        params:params
    })
}

//购买会员后修改用户VIP权限
export const updateMemberPower = params =>{
    return request({
        url:'/updateMemberPower',
        method:'post',
        params:params
    })
}


//购买会员后修改用户VIP权限
export const getMemberPower = params =>{
    return request({
        url:'/getMemberPower',
        method:'get',
        params:params
    })
}


//获取会员基本信息
export const getMemberByMemberNo = params =>{
    return request({
        url:'/getMemberByMemberNo',
        method:'get',
        params:params
    })
}

//获取会员基本信息
export const updateMemberPassword = params =>{
    return request({
        url:'/updateMemberPassword',
        method:'post',
        params:params
    })
}


// 签到
export const addCheckIn = params =>{
    return request({
        url:'/addCheckIn',
        method:'post',
        params:params
    })
}


// 签到
export const updateMemberChangeByMemberNo = params =>{
    return request({
        url:'/updateMemberChangeByMemberNo',
        method:'post',
        params:params
    })
}


// 获取季度会员数量
export const getMemberBySeason = params =>{
    return request({
        url:'/echarts/getMemberBySeason',
        method:'get',
        params:params
    })
}


// 获取年度男女人数
export const getMemberSexByYear = params =>{
    return request({
        url:'/echarts/getMemberSexByYear',
        method:'get',
        params:params
    })
}

//新增会员
export const register = params =>{
    return request({
        url:'/register',
        method:'post',
        data:params
    })
}


