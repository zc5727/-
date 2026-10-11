package com.gym.controller;

import com.gym.entity.Common;

import com.gym.entity.Equipment;
import com.gym.service.EquipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
public class EquipmentController {
    @Autowired
    private EquipmentService equipmentService;

    @RequestMapping(path= "/getAllEquipment")
    public List<Equipment> getAllEquipment(int page, int size){
        return equipmentService.getAllEquipment(page,size);
    }

    @RequestMapping(path= "/addEquipment")
    public Map<String,Object> addEquipment(Equipment equipment){
        return equipmentService.addEquipment(equipment);
    }

    @RequestMapping(path ="/updateEquipment")
    public Map<String,Object> updateEquipment(Equipment equipment){
        return equipmentService.updateEquipment(equipment);
    }

    @RequestMapping(path ="/deleteEquipment")
    public Map<String,Object> deleteEmployee(int equipmentNo){
        return equipmentService.deleteEquipment(equipmentNo);
    }

    @RequestMapping(path = "/totalEquipment")
    public Common totalEmployee() {
        return equipmentService.totalEquipment();
    }

    @RequestMapping(path = "/getByKeywordEquipment")
    public List<Equipment> getByKeywordEquipment(String keyWord, int page, int size){
        return equipmentService.getByKeywordEquipment(keyWord,page,size);
    }

    @RequestMapping(path = "/totalEquipmentFuzzy")
    public Common totalEquipmentFuzzy(String keyWord) {
        return equipmentService.totalEquipmentFuzzy(keyWord);
    }
}
