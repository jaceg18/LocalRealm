package com.jaceg18.localrealm.core.service;

import java.util.function.Supplier;

public class StatService  {

    public StatService(){

    }

    public void perform(Supplier<Void> task){
        task.get();
    }


}
