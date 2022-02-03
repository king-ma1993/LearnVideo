package com.myl.learnvideo.camera

import android.util.Size
import java.util.Comparator

// 为Size定义一个比较器Comparator
class CompareSizesByArea : Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size): Int {
        // 强转为long保证不会发生溢出
        return java.lang.Long.signum(
            lhs.width.toLong() * lhs.height -
                    rhs.width.toLong() * rhs.height
        )
    }
}