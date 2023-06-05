package org.wso2.micro.integrator.transport.handlers.requestprocessors.swagger.format;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class MediaTypeMixin {
    public MediaTypeMixin() {

    }

    @JsonIgnore
    public abstract void setExample(Object example);

    @JsonIgnore
    public abstract Object getExample();

    @JsonIgnore
    public abstract void setExampleSetFlag(boolean exampleSetFlag);

    @JsonIgnore
    abstract boolean getExampleSetFlag();
}
