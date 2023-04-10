package com.freegang.xpler.xp

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

/// 使用案例, 详见: https://github.com/GangJust/Xpler/blob/main/docs/readme.md

@Target(AnnotationTarget.FIELD)
annotation class FieldGet(val fieldName: String = "", val tag: String = "tag")

@Target(AnnotationTarget.FUNCTION)
annotation class OnBefore(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class OnAfter(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class OnReplace(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class OnConstructorBefore

@Target(AnnotationTarget.FUNCTION)
annotation class OnConstructorAfter

@Target(AnnotationTarget.FUNCTION)
annotation class OnConstructorReplace

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Param(val name: String)

abstract class KtOnHook<T>(protected val lpparam: XC_LoadPackage.LoadPackageParam) {
    private var mTargetClazz: Class<*>
    protected val targetClazz get() = mTargetClazz

    private var hookHelper: KtXposedHelpers? = null

    private val mineMethods = mutableSetOf<Method>()
    private val mineFields = mutableSetOf<Field>()

    init {
        mTargetClazz = this.setTargetClass()
        if (mTargetClazz.name != EmptyHook::class.java.name) {
            hookHelper = KtXposedHelpers.hookClass(targetClazz)

            getMineAllMethods()
            getMineAllFields()

            invOnBefore()
            invOnAfter()
            invOnReplace()

            invOnConstructorBefore()
            invOnConstructorAfter()
            invOnConstructorReplace()

            defaultHookAllMethod()
        }
        this.onInit()
    }

    open fun onInit() {}

    /**
     * 手动设置目标类, 通常在泛型<T>为Any时做替换, 常见情况是未对目标app类做只读引入,
     * 则需要通过: XposedHelpers.findClass("类名", lpparam.classLoader)
     */
    open fun setTargetClass(): Class<*> = getHookTargetClass()

    /**
     * 查找某类
     */
    fun findClass(className: String): Class<*> {
        return XposedHelpers.findClass(className, lpparam.classLoader)
    }

    /**
     * 获取泛型中的类, 如果泛型类是 Any, 则可通过 [setTargetClass] 对指定类进行设置
     */
    @Throws
    private fun getHookTargetClass(): Class<*> {
        val type = this::class.java.genericSuperclass as ParameterizedType
        return type.actualTypeArguments[0] as Class<*>
    }

    /**
     * 获取子类所有方法
     */
    private fun getMineAllMethods() {
        mineMethods.addAll(this::class.java.declaredMethods)
    }

    /**
     * 获取子类所有字段
     */
    private fun getMineAllFields() {
        mineFields.addAll(this::class.java.declaredFields)
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnBefore] 标注的所有方法, 并将其Hook
     */
    @Throws
    private fun invOnBefore() {
        val methodMap = getAnnotationMethod(OnBefore::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((key, value) in methodMap) {
            if (value.getAnnotation(OnReplace::class.java) != null) continue

            hookHelper?.method(key.name.ifEmpty { value.name }, *getTargetMethodParamTypes(value)) {
                onBefore {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs)
                }
            }
        }
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnAfter] 标注的所有方法, 并将其Hook
     */
    @Throws
    private fun invOnAfter() {
        val methodMap = getAnnotationMethod(OnAfter::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((key, value) in methodMap) {
            if (value.getAnnotation(OnReplace::class.java) != null) continue

            hookHelper?.method(key.name.ifEmpty { value.name }, *getTargetMethodParamTypes(value)) {
                onAfter {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs)
                }
            }
        }
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnReplace] 标注的所有方法,并将其Hook
     * 值得注意的是, 某个方法一旦标注了 [@OnReplace] 如果该方法又同时
     * 被[@OnBefore]或[@OnAfter]标注, 该方法跳过它们, 只对 [@OnReplace] 生效
     */
    @Throws
    private fun invOnReplace() {
        val methodMap = getAnnotationMethod(OnReplace::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((key, value) in methodMap) {
            hookHelper?.method(key.name.ifEmpty { value.name }, *getTargetMethodParamTypes(value)) {
                onReplace {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs) ?: Unit
                }
            }
        }
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnConstructorBefore] 标注的所有方法, 并将其Hook
     */
    @Throws
    private fun invOnConstructorBefore() {
        val methodMap = getAnnotationMethod(OnConstructorBefore::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((_, value) in methodMap) {
            if (value.getAnnotation(OnConstructorReplace::class.java) != null) continue

            hookHelper?.constructor(*getTargetMethodParamTypes(value)) {
                onBefore {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs)
                }
            }
        }
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnConstructorAfter] 标注的所有方法, 并将其Hook
     */
    @Throws
    private fun invOnConstructorAfter() {
        val methodMap = getAnnotationMethod(OnConstructorAfter::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((_, value) in methodMap) {
            if (value.getAnnotation(OnConstructorReplace::class.java) != null) continue

            hookHelper?.constructor(*getTargetMethodParamTypes(value)) {
                onAfter {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs)
                }
            }
        }
    }

    /**
     * 查找子类方法[mineMethods]中被 [@OnConstructorReplace] 标注的所有方法, 并将其Hook
     */
    @Throws
    private fun invOnConstructorReplace() {
        val methodMap = getAnnotationMethod(OnConstructorReplace::class.java)
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        for ((_, value) in methodMap) {
            hookHelper?.constructor(*getTargetMethodParamTypes(value)) {
                onReplace {
                    val invArgs = arrayOf(this, *args)
                    setAnyField(thisObject, fieldMap)
                    value.invoke(this@KtOnHook, *invArgs)
                    thisObject
                }
            }
        }
    }

    /**
     * 获取被指定注解标注的方法集合
     * @param a  a extends Annotation
     * @return Map
     */
    private fun <A : Annotation> getAnnotationMethod(a: Class<A>): Map<A, Method> {
        val map = mutableMapOf<A, Method>()
        for (method in mineMethods) {
            val annotation = method.getAnnotation(a) ?: continue

            val mineParamsTypes = method.parameterTypes
            if (mineParamsTypes.isEmpty()) {
                throw IllegalArgumentException("parameterTypes empty.")
            }
            if (mineParamsTypes.first() != XC_MethodHook.MethodHookParam::class.java) {
                throw IllegalArgumentException("parameterTypes[0] should be XC_MethodHook.MethodHookParam.")
            }

            map[annotation] = method
        }
        return map
    }

    /**
     * 替换@Param注解, 获取目标方法中的的真实参数列表
     * @param method 目标方法
     * @return Array
     */
    private fun getTargetMethodParamTypes(method: Method): Array<Class<*>> {
        //整理参数、参数注解列表, 将第一个参数`XC_MethodHook.MethodHookParam`移除
        val targetMethodParamTypes = method.parameterTypes.toMutableList().apply { removeFirst() }
        val parameterAnnotations = method.parameterAnnotations.toMutableList().apply { removeFirst() }

        //替换 @Param 指定的参数类型
        val finalTargetMethodParamTypes = targetMethodParamTypes.mapIndexed { index, clazz ->
            if (parameterAnnotations[index].isEmpty()) return@mapIndexed clazz //如果某个参数没有注解
            val param = parameterAnnotations[index].filterIsInstance<Param>().ifEmpty { return@mapIndexed clazz } //如果某个参数有注解, 但没有@Param注解
            findClass(param[0].name) //寻找注解类
        }.toTypedArray()

        return finalTargetMethodParamTypes
    }

    /**
     * 默认Hook目标类的所有函数调用, 为字段注入值
     */
    private fun defaultHookAllMethod() {
        val fieldMap = getAnnotationFiled(FieldGet::class.java)
        hookHelper?.methodAll {
            onAfter {
                thisObject ?: return@onAfter
                setAnyField(thisObject, fieldMap)
            }
        }
    }

    /**
     * 查找子类字段[mineFields]中被[@FieldGet]标注的所有字段, 并赋值
     */
    @Throws
    private fun <A : Annotation> setAnyField(instance: Any, fieldMap: Map<A, Field>) {
        for ((key, value) in fieldMap) {
            key as FieldGet
            val any = instance.getObjectField<Any>(key.fieldName.ifEmpty { value.name })
            value.set(this@KtOnHook, any)
        }
    }

    /**
     * 获取被指定注解标注的字段集合
     */
    private fun <A : Annotation> getAnnotationFiled(a: Class<A>): Map<A, Field> {
        val fieldMap = mutableMapOf<A, Field>()
        for (field in mineFields) {
            val annotation = field.getAnnotation(a) ?: continue
            field.isAccessible = true
            fieldMap[annotation] = field
        }
        return fieldMap
    }
}

class EmptyHook {}