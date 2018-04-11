package uk.ac.ebi.spot.goci.curation.service.reports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.goci.curation.model.reports.ReportsWeeklyProgressView;
import uk.ac.ebi.spot.goci.model.EventType;
import uk.ac.ebi.spot.goci.model.WeeklyProgressView;
import uk.ac.ebi.spot.goci.repository.WeeklyProgressViewRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by emma on 08/06/2016.
 *
 * @author emma
 *         <p>
 *         Service to create weekly progress views
 */
@Lazy
@Service
public class WeeklyProgressService {

    private WeeklyProgressViewRepository weeklyProgressViewRepository;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Autowired
    public WeeklyProgressService(WeeklyProgressViewRepository weeklyProgressViewRepository) {
        this.weeklyProgressViewRepository = weeklyProgressViewRepository;
    }

    public List<ReportsWeeklyProgressView> processWeeklyView() {

        getLog().info("Creating weekly progress view");

        Set<Long> previouslyPublished_Studies = new HashSet<>();
        Set<Long> previousLevel1_CurationDone_Studies = new HashSet<>();
        Set<Long> previousLevel2_CurationDone_Studies = new HashSet<>();

        // Get all details from database
        List<WeeklyProgressView> weeklyProgressViews = weeklyProgressViewRepository.findAll();

        // Create a list of views that will be returned to the controller and passed to the view
        List<ReportsWeeklyProgressView> reportsWeeklyProgressViews = new ArrayList<>();

        // As a convenience get a unique set of week start dates
//        Set<Date> uniqueWeekSet = weeklyProgressViews.stream()
//                .map(WeeklyProgressView::getWeekStartDay)
//                .distinct()
//                .collect(Collectors.toSet());
//        uniqueWeekSet.forEach(date -> {

        // Get all unique start week dates as Custom Query
        List<Date> uniqueWeekStartDate = weeklyProgressViewRepository.getAllWeekStartDates();

        uniqueWeekStartDate.forEach(date -> {

            // For each date create a view object
            ReportsWeeklyProgressView reportsWeeklyProgressView = new ReportsWeeklyProgressView(date);

            // Get a unique set of study IDs created in that week
            Set<Long> studiesCreatedThatWeek = weeklyProgressViews.stream()
                    .filter(weeklyProgressView -> weeklyProgressView.getWeekStartDay().equals(date))
                    .filter(weeklyProgressView -> weeklyProgressView.getEventType().equals(
                            "STUDY_CREATION"))
                    .map(WeeklyProgressView::getStudyId)
                    .collect(Collectors.toSet());

            /*
                Get a unique set of study IDs published in that week
             */
            Set<Long> studiesPublishedThatWeek = weeklyProgressViews.stream()
                    .filter(weeklyProgressView -> weeklyProgressView.getWeekStartDay().equals(date))
                    .filter(weeklyProgressView -> weeklyProgressView.getEventType().equals(
                            "STUDY_STATUS_CHANGE_PUBLISH_STUDY"))
                    .map(WeeklyProgressView::getStudyId)
                    .collect(Collectors.toSet());

            // Remove any study ID that was previously published
            studiesPublishedThatWeek.removeAll(previouslyPublished_Studies);

            // Add set of study IDs to set of previouslyPublished
            previouslyPublished_Studies.addAll(studiesPublishedThatWeek);


            /*
                Get a unique set of study IDs that went through Level 1 curation in that week
             */
            Set<Long> studiesWithLevel1Completed = weeklyProgressViews.stream()
                    .filter(weeklyProgressView -> weeklyProgressView.getWeekStartDay().equals(date))
                    .filter(weeklyProgressView -> weeklyProgressView.getEventType().equals(
                            "STUDY_STATUS_CHANGE_LEVEL_1_CURATION_DONE"))
                    .map(WeeklyProgressView::getStudyId)
                    .collect(Collectors.toSet());

            // Remove any study ID with status Level1_CurationDone
            studiesWithLevel1Completed.removeAll(previousLevel1_CurationDone_Studies);

            // Add set of study IDs to set of previouslyLevel1_CurationDone
            previousLevel1_CurationDone_Studies.addAll(studiesWithLevel1Completed);


            /*
                Get a unique set of study IDs that went through Level 2 curation in that week
            */
            Set<Long> studiesWithLevel2Completed = weeklyProgressViews.stream()
                    .filter(weeklyProgressView -> weeklyProgressView.getWeekStartDay().equals(date))
                    .filter(weeklyProgressView -> weeklyProgressView.getEventType().equals(
                            "STUDY_STATUS_CHANGE_LEVEL_2_CURATION_DONE"))
                    .map(WeeklyProgressView::getStudyId)
                    .collect(Collectors.toSet());

            // Remove any study ID with status Level1_CurationDone
            studiesWithLevel2Completed.removeAll(previousLevel2_CurationDone_Studies);

            // Add set of study IDs to set of previouslyLevel1_CurationDone
            previousLevel2_CurationDone_Studies.addAll(studiesWithLevel2Completed);


            // Add sets to view object
            reportsWeeklyProgressView.setStudiesCreated(studiesCreatedThatWeek);
            reportsWeeklyProgressView.setStudiesPublished(studiesPublishedThatWeek);
            reportsWeeklyProgressView.setStudiesLevel1Completed(studiesWithLevel1Completed);
            reportsWeeklyProgressView.setStudiesLevel2Completed(studiesWithLevel2Completed);
            reportsWeeklyProgressViews.add(reportsWeeklyProgressView);
        });
        return reportsWeeklyProgressViews;
    }
}
