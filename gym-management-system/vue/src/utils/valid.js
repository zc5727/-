export default {
    required: (message) => {
        if (!message) {
            message = '* 必填';
        }
        return { required: true, message: message, trigger: 'change' };
    },
    required_trigger: (message, trigger) => {
        return { required: true, message: message, trigger: trigger };
    },
    required_type: (message, type) => {
        return { type: type, required: true, message: message, trigger: 'change' };
    },
    mobile: () => {
        return {
            required:true,
            message: '手机号格式错误',
            trigger: 'change',
            pattern: /^(?:(?:\+|00)86)?1[3-9]\d{9}$/
        }
    },
    IdCard: () => {
        return {
            message: '身份证格式错误',
            trigger: 'change',
            pattern: /^[1-9]\d{5}(?:18|19|20)\d{2}(?:0[1-9]|10|11|12)(?:0[1-9]|[1-2]\d|30|31)\d{3}[\dXx]$/
        }
    },
    age: () => {
        return {
            required:true,
            message: '年龄必须在0-100之间',
            pattern: /^(?:(?:\+|00)86)?1[3-9]\d{9}$/
        }
    },

}