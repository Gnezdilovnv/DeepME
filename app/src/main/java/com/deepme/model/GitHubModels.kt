package com.deepme.model
data class Repository(val id:Long,val name:String,val full_name:String,val html_url:String,val description:String?,val private:Boolean,val default_branch:String)
data class CreateRepoRequest(val name:String,val description:String?=null,val private:Boolean=false,val auto_init:Boolean=true)
data class ContentItem(val name:String,val path:String,val sha:String,val size:Int,val type:String,val content:String?=null,val encoding:String?=null)
data class FileContentRequest(val message:String,val content:String,val sha:String?=null,val branch:String?=null)
data class DeleteFileRequest(val message:String,val sha:String,val branch:String?=null)
data class DevEnvironment(val id:String,val name:String,val language:String,val installed:Boolean=false)
object Environments{val list=listOf(DevEnvironment("python","Python 3","python"),DevEnvironment("node","Node.js","javascript"),DevEnvironment("go","Go","go"),DevEnvironment("rust","Rust","rust"),DevEnvironment("java","Java 17","java"),DevEnvironment("kotlin","Kotlin","kotlin"),DevEnvironment("c_cpp","C/C++","c"));fun getSetupCmd(e:String)=when(e){"python"->"pkg install python -y && pip install flask";"node"->"pkg install nodejs -y";"go"->"pkg install golang -y";"rust"->"pkg install rust -y";"java"->"pkg install openjdk-17 -y";"kotlin"->"pkg install kotlin -y";"c_cpp"->"pkg install clang make -y";else->"pkg update -y"}}