package me.anno.utils.test

import java.io.File
import java.lang.RuntimeException

fun main(){

    // didn't show the error;
    // the error was missing(?) \url tags... in bibtex
    // why ever you have to set them in JabRef... I've the feeling it should be done automatically
 
    val file = File("C:/Users/Antonio/Desktop/test.txt")
    val str = file.readText()

    var i = 0
    var d = 0
    while(i < str.length){
        when(str[i++]){
            '{' -> {
                d++
            }
            '}' -> {
                d--
                if(d < 0) throw RuntimeException()
            }
            '\\' ->{
                i++
            }
        }
    }
    if(d > 0) throw RuntimeException()

}