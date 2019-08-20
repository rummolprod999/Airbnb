package anbapp

import anbapp.builderApp.Builder
import anbapp.executor.Executor


fun main(args: Array<String>) {
    val b = Builder(args)
    b.deleteBigLog()
    Executor()
}