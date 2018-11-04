package io.ghostbuster91.docker.compose.logger

import io.reactivex.Observable
import org.junit.Test

class ATest {

    @Test
    fun name() {
        Observable.fromIterable(listOf(1,1,2,2,1,1))
                .scan(""){ acc, item ->
                    if(item == 1){
                        "1"
                    }else{
                        acc + item
                    }
                }
                .skip(1)
                .doAfterNext { println(it) }
                .blockingSubscribe()
    }
}