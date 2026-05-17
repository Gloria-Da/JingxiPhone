package com.yoyo.jingxi.network;

import java.util.List;

public class ModelListResponse {
    public List<Model> data;

    public static class Model {
        public String id;
        public String object;
    }
}
