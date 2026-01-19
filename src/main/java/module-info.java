module com.tonyguerra.net.tcpmaster {
    requires org.slf4j;
    requires org.reflections;

    exports com.tonyguerra.net.tcpmaster.core;
    exports com.tonyguerra.net.tcpmaster.handlers;
    exports com.tonyguerra.net.tcpmaster.errors;
    exports com.tonyguerra.net.tcpmaster.enums;
    exports com.tonyguerra.net.tcpmaster.di;
    exports com.tonyguerra.net.tcpmaster.configurations;
    exports com.tonyguerra.net.tcpmaster.core.components;

    opens com.tonyguerra.net.tcpmaster.handlers to org.reflections;
    opens com.tonyguerra.net.tcpmaster.standard to org.reflections;
}
