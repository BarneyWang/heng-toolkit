package me.heng.tool.akka;

import akka.actor.*;
import akka.dispatch.MailboxType;
import akka.dispatch.MessageDispatcher;
import akka.pattern.Patterns;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.io.Serializable;

/**
 * 提供akka
 */
public class Actors {

    public static final TypedMessage ECHO = new TypedMessage("__echo__");

    public static class TypedMessage implements Serializable {
        private String value;

        public TypedMessage(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TypedMessage) {
                return this.value.equals(((TypedMessage) obj).value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    /**
     * @refer akka.testkit.CALLING_THREAD_DISPATCHER
     */
    private static final String CALLING_THREAD_DISPATCHER = "akka.test.calling-thread-dispatcher";

    public static class ActorRefWithType<T> extends LocalActorRef {

        public ActorRefWithType(ActorSystemImpl psystem, Props pprops,
                MessageDispatcher pdispatcher, MailboxType pmailboxType,
                InternalActorRef psupervisor, ActorPath path) {
            super(psystem, pprops, pdispatcher, pmailboxType, psupervisor, path);
        }

        private T innerInstance;

        public T getTypeRef() throws Exception {
            if (innerInstance != null) {
                return innerInstance;
            }
            if (underlying().actor()!=null) {
                innerInstance = (T) underlying().actor();
                return innerInstance;
            }
            /**
             * akka 实例化actor有类似懒加载的方式, LocalActorRef要向它发送消息,才实例化;underlying().actor()才有值
             */
            Future<Object> asked = Patterns.ask(this, ECHO, 1 * 1000);
            Object result = Await.result(asked, AkkaSupport.millis(1 * 1000));
            return getTypeRef();
        }

    }

    public static <T> ActorRefWithType createTypedActor(ActorSystemImpl system, Props props,
            String name, ActorRef supervisor) {
        /**
         * @refer akka.testkit.TestActorRef
         */
        Props insideProps = props;
        MessageDispatcher insideDispatcher = system.dispatchers().defaultGlobalDispatcher();
        if (! Deploy.NoDispatcherGiven().equals(insideProps.deploy().dispatcher())) {
            insideDispatcher = system.dispatchers().lookup(insideProps.dispatcher());
        }

        MailboxType insideMailboxType =
                system.mailboxes().getMailboxType(insideProps, insideDispatcher.configurator().config());
        ActorPath path = supervisor.path().$div(name);
        if (! (supervisor instanceof LocalActorRef)) {
            throw new RuntimeException("supervisor is instance of LocalActorRef");
        }
        InternalActorRef insideSupervisor = (InternalActorRef) supervisor;
        ActorRefWithType<T> actor = new ActorRefWithType<>(system, insideProps, insideDispatcher,
                insideMailboxType, insideSupervisor, path);
        return actor;
    }

    public static <T> ActorRefWithType createTypedActor(ActorSystem system, Props props,
            String name) {
        if (system instanceof ActorSystemImpl) {
            ActorSystemImpl insideSystem = (ActorSystemImpl) system;
            LocalActorRef guardian = insideSystem.guardian();
            return createTypedActor(insideSystem, props, name, guardian);
        }
        throw new RuntimeException("");
    }
}
