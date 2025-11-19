package com.example.twilightbossaddon.client;

import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.client.model.HumanoidModel; // !! 导入 HumanoidModel !!
import net.minecraft.client.model.geom.ModelPart;

/**
 * 这是Boss的模型类（骨架）
 * 我们暂时继承 HumanoidModel (人形模型)，这完全符合你一阶段“人形”的设计
 */
public class NarratorModel<T extends NarratorEntity> extends HumanoidModel<T> {
    public NarratorModel(ModelPart pRoot) {
        super(pRoot);
    }
}